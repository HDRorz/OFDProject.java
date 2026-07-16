package hd.ofdproject.ofdfile;

import java.io.*;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;

/**
 * 中登文件快速读取器
 * 通过优化的缓冲区读取和字节处理提升性能
 */
public class OFDFileFastReader {
    
    private static final Charset GB_ENCODING = Charset.forName("GB18030");
    private static final byte B_BLANK = 32; // 空格字节
    private static final char C_BLANK = (char) 32; // 空格字符
    private static final int BUFFER_SIZE = 1024;
    
    private Map<String, OFDFieldInfo> fieldInfoDictV21;
    private Map<String, OFDFieldInfo> fieldInfoDictV22;
    
    public OFDFileFastReader() {
        init();
    }
    
    private void init() {
        try {
            fieldInfoDictV21 = initFieldConfig("/FieldInfo21.txt");
            fieldInfoDictV22 = initFieldConfig("/FieldInfo22.txt");
        } catch (Exception e) {
            try {
                fieldInfoDictV21 = initFieldConfig("/FieldInfo.txt");
            } catch (IOException ex) {
                throw new RuntimeException("无法加载字段配置文件", ex);
            }
        }
    }
    
    private Map<String, OFDFieldInfo> initFieldConfig(String resourcePath) throws IOException {
        Map<String, OFDFieldInfo> dict = new HashMap<>();
        
        InputStream is = getClass().getResourceAsStream(resourcePath);
        if (is == null) {
            throw new FileNotFoundException("配置文件不存在: " + resourcePath);
        }
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, GB_ENCODING))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                String[] items = line.split(",");
                if (items.length >= 6) {
                    String fieldDesc = items.length > 6 ? items[6] : "";
                    dict.put(items[2].toLowerCase(), new OFDFieldInfo(items[2], items[3], items[4], items[5], fieldDesc));
                }
            }
        }
        
        return dict;
    }
    
    /**
     * 快速读取中登文件
     */
    public OFDFile readFile(String fileName) throws IOException {
        OFDFileHeader fileHeader = new OFDFileHeader();
        fileHeader.setFileName(fileName);
        List<OFDFieldInfo> fieldInfos = new ArrayList<>();
        List<byte[]> datas = new ArrayList<>();
        Map<String, OFDFieldInfo> curFieldsDict = fieldInfoDictV21;
        
        try (FileChannel channel = FileChannel.open(Paths.get(fileName), StandardOpenOption.READ);
            LineReader reader = new LineReader(channel)) {
            
            // 读取前9行文件头
            String ofdcfdat = reader.readLine();
            fileHeader.setFileVersion(reader.readLine().trim());
            fileHeader.setTaNo(reader.readLine().trim());
            fileHeader.setFileReceiver(reader.readLine().trim());
            fileHeader.setDate(reader.readLine().trim());
            fileHeader.setFileNo(reader.readLine().trim());
            fileHeader.setFileType(reader.readLine().trim());
            fileHeader.setDataSender(reader.readLine().trim());
            fileHeader.setDataReceiver(reader.readLine().trim());
            
            int fileVersion = Integer.parseInt(fileHeader.getFileVersion());
            if (fileVersion >= 22 && fieldInfoDictV22 != null) {
                curFieldsDict = fieldInfoDictV22;
            }
            
            String fileType = fileHeader.getFileType();
            if ("31".equals(fileType) || "32".equals(fileType) 
                || "43".equals(fileType) || "44".equals(fileType)) {
                curFieldsDict = fieldInfoDictV21;
            }
            
            String fieldCountStr = reader.readLine();
            int fieldCount = Integer.parseInt(fieldCountStr);
            int rowByteCount = 0;
            
            // 读取文件字段列表
            for (int i = 0; i < fieldCount; i++) {
                String colName = reader.readLine().toLowerCase();
                
                OFDFieldInfo fieldInfo = curFieldsDict.get(colName);
                if (fieldInfo != null) {
                    rowByteCount += fieldInfo.getFieldSize();
                    fieldInfos.add(fieldInfo);
                } else {
                    throw new RuntimeException(String.format("%s，文件字段%s信息未配置", fileName, colName));
                }
            }
            
            int rowCount = Integer.parseInt(reader.readLine());
            
            // 读取数据行 - 统一使用LineReader读取固定长度的数据行
            datas = new ArrayList<>(rowCount);
            
            for (int i = 0; i < rowCount; i++) {
                byte[] lineBuffer = reader.readFixedLengthData(rowByteCount);
                datas.add(lineBuffer);
            }

            // 验证文件结尾
            String ofdcfend = reader.readLine().trim();
            if (!"OFDCFEND".equals(ofdcfend)) {
                throw new RuntimeException(String.format("%s，文件结尾不为OFDCFEND", fileName));
            }
        }
        
        OFDFile fileData = new OFDFile(fileHeader, fieldInfos, datas);
        fileData.setDeserializeRowDataFunc(OFDFileFastReader::deserializeRowData);
        return fileData;
    }
    
    /**
     * 简洁的行读取器
     */
    private static class LineReader implements AutoCloseable {
        private final FileChannel channel;
        private final ByteBuffer buffer;
        
        LineReader(FileChannel channel) {
            this.channel = channel;
            this.buffer = ByteBuffer.allocate(BUFFER_SIZE);
            this.buffer.flip(); // 初始为空
        }
        
        /**
         * 读取一行
         */
        String readLine() throws IOException {
            ByteArrayOutputStream line = new ByteArrayOutputStream(128);
            
            while (true) {
                // Buffer读完了,重新填充
                if (!buffer.hasRemaining()) {
                    buffer.clear();
                    int bytesRead = channel.read(buffer);
                    if (bytesRead == -1) {
                        // 文件结束
                        return line.size() > 0 ? new String(line.toByteArray(), GB_ENCODING) : "";
                    }
                    buffer.flip();
                }
                
                byte b = buffer.get();
                
                // 处理换行符
                if (b == '\r') {
                    // 检查是否跟着 \n
                    if (buffer.hasRemaining() && buffer.get(buffer.position()) == '\n') {
                        buffer.get(); // 消费 \n
                    }
                    break;
                } else if (b == '\n') {
                    break;
                }
                
                line.write(b);
            }
            
            return new String(line.toByteArray(), GB_ENCODING);
        }
        
        /**
         * 读取固定长度的数据，包括行尾的\r\n
         */
        byte[] readFixedLengthData(int dataLength) throws IOException {
            byte[] result = new byte[dataLength];
            int totalRead = 0;
            
            // 读取指定长度的数据
            while (totalRead < dataLength) {
                // Buffer读完了,重新填充
                if (!buffer.hasRemaining()) {
                    buffer.clear();
                    int bytesRead = channel.read(buffer);
                    if (bytesRead == -1) {
                        throw new RuntimeException("文件意外结束，无法读取完整的数据行");
                    }
                    buffer.flip();
                }
                
                int remainingToRead = dataLength - totalRead;
                int availableInBuffer = buffer.remaining();
                int toRead = Math.min(remainingToRead, availableInBuffer);
                
                buffer.get(result, totalRead, toRead);
                totalRead += toRead;
            }
            
            // 读取并验证行尾的 \r\n
            byte cr = readByte();
            byte lf = readByte();
            
            if (cr != 13 || lf != 10) {
                throw new RuntimeException("数据行格式错误，行尾不是\\r\\n");
            }
            
            return result;
        }
        
        /**
         * 读取单个字节
         */
        private byte readByte() throws IOException {
            if (!buffer.hasRemaining()) {
                buffer.clear();
                int bytesRead = channel.read(buffer);
                if (bytesRead == -1) {
                    throw new RuntimeException("文件意外结束");
                }
                buffer.flip();
            }
            return buffer.get();
        }
        
        @Override
        public void close() {
            // FileChannel由外部管理
        }
    }
    
    /**
     * 优化的反序列化方法
     */
    public static Object[] deserializeRowData(List<OFDFieldInfo> fieldProperties, byte[] content) {
        Object[] dataArray = new Object[fieldProperties.size()];
        int index = 0;
        
        for (int j = 0; j < fieldProperties.size(); j++) {
            OFDFieldInfo property = fieldProperties.get(j);
            
            try {
                if ("TEXT".equals(property.getFieldType()) 
                    || property.getFieldSize() == OFDFieldInfo.STRING_MAX_LENGTH) {
                    byte[] tempBuffer = new byte[content.length - index];
                    System.arraycopy(content, index, tempBuffer, 0, tempBuffer.length);
                    dataArray[j] = new String(tempBuffer, GB_ENCODING).trim();
                    break;
                }
                
                if ("N".equals(property.getFieldType())) {
                    // 数字长度大于16会超过long上限
                    if (property.getFieldSize() > 16) {
                        int intSize = property.getFieldSize() - property.getFieldSize2();
                        String value = fastReadASCII(content, index, property.getFieldSize());
                        value = new StringBuilder(value).insert(intSize, ".").toString();
                        dataArray[j] = new BigDecimal(value);
                    } else {
                        if (property.getFieldSize2() == 0) {
                            dataArray[j] = new BigDecimal(fastByte2Long(content, index, property.getFieldSize()));
                        } else {
                            dataArray[j] = fastByte2Decimal(content, index, property.getFieldSize(), property.getFieldSize2());
                        }
                    }
                } else if ("A".equals(property.getFieldType())) {
                    String value = fastReadASCII(content, index, property.getFieldSize());
                    dataArray[j] = value.trim();
                } else {
                    String value = new String(content, index, property.getFieldSize(), GB_ENCODING);
                    dataArray[j] = value.trim();
                }
                
                index += property.getFieldSize();
            } catch (Exception ex) {
                String rowContent = new String(content, GB_ENCODING);
                String fieldContent = new String(content, index, 
                    Math.min(property.getFieldSize(), content.length - index), GB_ENCODING);
                throw new RuntimeException(
                    String.format("%d列，字段名=%s，一行内容=%s，字段范围内容=%s", 
                        j, property.getFieldName(), rowContent, fieldContent), ex);
            }
        }
        
        return dataArray;
    }
    
    /**
     * 快速将字节转为长整型
     */
    private static long fastByte2Long(byte[] content, int startIdx, int length) {
        long ret = 0;
        int i = 0;
        
        if (content[startIdx] == '-') {
            i++;
            for (; i < length; i++) {
                int curnum = content[startIdx + i] - '0';
                if (curnum > 9 || curnum < 0) {
                    throw new RuntimeException("无法将'" + (char)content[startIdx + i] + "'转为数字");
                }
                ret = ret * 10 + curnum;
            }
            ret = ret * -1;
        } else {
            for (; i < length; i++) {
                int curnum = content[startIdx + i] - '0';
                if (curnum > 9 || curnum < 0) {
                    throw new RuntimeException("无法将'" + (char)content[startIdx + i] + "'转为数字");
                }
                ret = ret * 10 + curnum;
            }
        }
        
        return ret;
    }
    
    /**
     * 快速将字节转为Decimal
     */
    private static BigDecimal fastByte2Decimal(byte[] content, int startIdx, int length, int scale) {
        long tmpnum = fastByte2Long(content, startIdx, length);
        BigDecimal decimal = new BigDecimal(tmpnum);
        // 除以 10^scale 来设置小数位
        return decimal.movePointLeft(scale);
    }
    
    /**
     * 快速读取ASCII字符串
     */
    private static String fastReadASCII(byte[] content, int startIdx, int length) {
        // 检查是否全是空格
        if (content[startIdx] == B_BLANK) {
            boolean allBlank = true;
            for (int i = startIdx + 1; i < startIdx + length; i++) {
                if (content[i] != B_BLANK) {
                    allBlank = false;
                    break;
                }
            }
            if (allBlank) {
                return "";
            }
        }
        
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append((char) content[startIdx + i]);
        }
        return sb.toString();
    }
}
