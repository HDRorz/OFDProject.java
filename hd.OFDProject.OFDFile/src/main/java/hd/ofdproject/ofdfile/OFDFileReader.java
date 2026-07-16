package hd.ofdproject.ofdfile;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * 中登文件读取器
 */
public class OFDFileReader {
    
    private static final Charset GB_ENCODING = Charset.forName("GB18030");
    
    private Map<String, OFDFieldInfo> fieldInfoDictV21;
    private Map<String, OFDFieldInfo> fieldInfoDictV22;
    
    public OFDFileReader() {
        init();
    }
    
    private void init() {
        // 尝试从 classpath 加载配置文件
        try {
            fieldInfoDictV21 = initFieldConfig("/FieldInfo21.txt");
            fieldInfoDictV22 = initFieldConfig("/FieldInfo22.txt");
        } catch (Exception e) {
            // 如果V22不存在，使用默认配置
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
     * 读取中登文件
     */
    public OFDFile readFile(String fileName) throws IOException {
        OFDFileHeader fileHeader = new OFDFileHeader();
        fileHeader.setFileName(fileName);
        List<OFDFieldInfo> fieldInfos = new ArrayList<>();
        List<byte[]> datas = new ArrayList<>();
        Map<String, OFDFieldInfo> curFieldsDict = fieldInfoDictV21;
        
        // 使用 NIO Files.newBufferedReader 替代传统IO
        try (BufferedReader sr = Files.newBufferedReader(Paths.get(fileName), GB_ENCODING)) {
            
            // 读取前9行文件头
            String ofdcfdat = sr.readLine();
            fileHeader.setFileVersion(sr.readLine().trim());
            fileHeader.setTaNo(sr.readLine().trim());
            fileHeader.setFileReceiver(sr.readLine().trim());
            fileHeader.setDate(sr.readLine().trim());
            fileHeader.setFileNo(sr.readLine().trim());
            fileHeader.setFileType(sr.readLine().trim());
            fileHeader.setDataSender(sr.readLine().trim());
            fileHeader.setDataReceiver(sr.readLine().trim());
            
            int fileVersion = Integer.parseInt(fileHeader.getFileVersion());
            if (fileVersion >= 22 && fieldInfoDictV22 != null) {
                curFieldsDict = fieldInfoDictV22;
            }
            
            // 特定文件类型使用V21配置
            String fileType = fileHeader.getFileType();
            if ("31".equals(fileType) || "32".equals(fileType) 
                || "43".equals(fileType) || "44".equals(fileType)) {
                curFieldsDict = fieldInfoDictV21;
            }
            
            // 读取字段数量
            String fieldCountStr = sr.readLine();
            int fieldCount = Integer.parseInt(fieldCountStr);
            
            // 读取文件字段列表
            for (int i = 0; i < fieldCount; i++) {
                String colName = sr.readLine().trim().toLowerCase();
                OFDFieldInfo fieldInfo = curFieldsDict.get(colName);
                if (fieldInfo != null) {
                    fieldInfos.add(fieldInfo);
                } else {
                    throw new RuntimeException(String.format("%s，文件字段%s信息未配置", fileName, colName));
                }
            }
            
            // 读取数据行数
            int rowCount = Integer.parseInt(sr.readLine());
            
            // 读取每行数据
            datas = new ArrayList<>(rowCount);
            for (int i = 0; i < rowCount; i++) {
                String line = sr.readLine();
                if (line != null) {
                    datas.add(line.getBytes(GB_ENCODING));
                }
            }
            
            // 验证文件结尾
            String ofdcfend = sr.readLine().trim();
            if (!"OFDCFEND".equals(ofdcfend)) {
                throw new RuntimeException(String.format("%s，文件结尾不为OFDCFEND", fileName));
            }
        }
        
        return new OFDFile(fileHeader, fieldInfos, datas);
    }
    
    /**
     * 反序列化行数据
     */
    public static Object[] deserializeRowData(List<OFDFieldInfo> fieldProperties, byte[] content) {
        Object[] dataArray = new Object[fieldProperties.size()];
        int index = 0;
        
        for (int j = 0; j < fieldProperties.size(); j++) {
            OFDFieldInfo property = fieldProperties.get(j);
            
            try {
                // TEXT类型或超长字段，读取剩余所有内容
                if ("TEXT".equals(property.getFieldType()) 
                    || property.getFieldSize() == OFDFieldInfo.STRING_MAX_LENGTH) {
                    byte[] tempBuffer = new byte[content.length - index];
                    System.arraycopy(content, index, tempBuffer, 0, tempBuffer.length);
                    dataArray[j] = new String(tempBuffer, GB_ENCODING);
                    break;
                }
                
                // 数字类型
                if ("N".equals(property.getFieldType())) {
                    int intSize = property.getFieldSize() - property.getFieldSize2();
                    String value = new String(content, index, property.getFieldSize(), GB_ENCODING);
                    // 插入小数点
                    value = new StringBuilder(value).insert(intSize, ".").toString();
                    
                    // 兼容负数
                    String fieldNameUpper = property.getFieldName().toUpperCase();
                    if ("NAV".equals(fieldNameUpper) || "FUNDSIZE".equals(fieldNameUpper)) {
                        if (value.contains("-")) {
                            value = value.replace("-", "0");
                            dataArray[j] = new BigDecimal(value).negate();
                        } else {
                            dataArray[j] = new BigDecimal(value);
                        }
                    } else {
                        dataArray[j] = new BigDecimal(value);
                    }
                } else {
                    // 字符串类型
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
}
