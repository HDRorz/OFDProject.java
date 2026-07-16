package hd.ofdproject.ofdfile;

import java.io.*;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 中登文件写入器
 */
public class OFDFileWriter {
    
    private static final Charset GB_ENCODING = Charset.forName("GB18030");
    private static final String NEW_LINE = "\r\n";
    
    private Map<String, OFDFieldInfo> fieldInfoDictV21;
    private Map<String, OFDFieldInfo> fieldInfoDictV22;
    
    public OFDFileWriter() {
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
                    dict.put(items[2].toLowerCase(), new OFDFieldInfo(items[2], items[3], items[4], items[5]));
                }
            }
        }
        
        return dict;
    }
    
    /**
     * 从原始 Object[] 生成文件
     */
    public byte[] createFileFromRawArray(OFDFileHeader header, List<String> fieldNameList, List<Object[]> datas) throws IOException {
        Map<String, OFDFieldInfo> curFieldsDict = fieldInfoDictV21;
        List<OFDFieldInfo> fieldInfos = new ArrayList<>();
        
        int fileVersion = Integer.parseInt(header.getFileVersion());
        if (fileVersion >= 22 && fieldInfoDictV22 != null) {
            curFieldsDict = fieldInfoDictV22;
        }
        
        // 获取字段信息
        for (String fieldName : fieldNameList) {
            OFDFieldInfo fieldInfo = curFieldsDict.get(fieldName.toLowerCase());
            if (fieldInfo != null) {
                fieldInfos.add(fieldInfo);
            } else {
                throw new RuntimeException(String.format("%s，文件字段%s信息未配置", header.getFileName(), fieldName));
            }
        }
        
        try (ByteArrayOutputStream ms = new ByteArrayOutputStream();
             OutputStreamWriter osw = new OutputStreamWriter(ms, GB_ENCODING);
             BufferedWriter writer = new BufferedWriter(osw)) {
            
            writeFileHeader(writer, header, fieldNameList);
            writeFileDatas(writer, fieldInfos, datas);
            
            writer.flush();
            return ms.toByteArray();
        }
    }
    
    /**
     * 从对象列表生成文件
     */
    public <T> byte[] createFile(OFDFileHeader header, List<T> datas, Class<T> type) throws IOException {
        List<String> fieldNames = new ArrayList<>();
        List<Field> fields = Arrays.asList(type.getDeclaredFields());
        
        for (Field field : fields) {
            field.setAccessible(true);
            fieldNames.add(field.getName());
        }
        
        List<Object[]> rawArrayDatas = new ArrayList<>();
        for (T data : datas) {
            Object[] objs = new Object[fields.size()];
            for (int i = 0; i < fields.size(); i++) {
                try {
                    objs[i] = fields.get(i).get(data);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException("无法访问字段", e);
                }
            }
            rawArrayDatas.add(objs);
        }
        
        return createFileFromRawArray(header, fieldNames, rawArrayDatas);
    }
    
    /**
     * 从对象列表生成文件（指定字段）
     */
    public <T> byte[] createFile(OFDFileHeader header, List<String> fieldNameList, List<T> datas, Class<T> type) throws IOException {
        List<Field> fields = new ArrayList<>();
        
        for (String fieldName : fieldNameList) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                fields.add(field);
            } catch (NoSuchFieldException e) {
                throw new RuntimeException(String.format("fieldName：%s，无法在%s中找到对应属性", fieldName, type.getName()));
            }
        }
        
        List<Object[]> rawArrayDatas = new ArrayList<>();
        for (T data : datas) {
            Object[] objs = new Object[fields.size()];
            for (int i = 0; i < fields.size(); i++) {
                try {
                    objs[i] = fields.get(i).get(data);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException("无法访问字段", e);
                }
            }
            rawArrayDatas.add(objs);
        }
        
        return createFileFromRawArray(header, fieldNameList, rawArrayDatas);
    }
    
    /**
     * 从字典列表生成文件
     */
    public byte[] createFileFromMaps(OFDFileHeader header, List<String> fieldNameList, 
                                      List<Map<String, Object>> datas) throws IOException {
        List<Object[]> rawArrayDatas = new ArrayList<>();
        
        for (Map<String, Object> map : datas) {
            Object[] objs = new Object[fieldNameList.size()];
            for (int i = 0; i < fieldNameList.size(); i++) {
                objs[i] = map.get(fieldNameList.get(i));
            }
            rawArrayDatas.add(objs);
        }
        
        return createFileFromRawArray(header, fieldNameList, rawArrayDatas);
    }
    
    /**
     * 写入文件头
     */
    private void writeFileHeader(BufferedWriter writer, OFDFileHeader header, List<String> fieldNameList) 
            throws IOException {
        writer.write("OFDCFDAT");
        writer.write(NEW_LINE);
        writer.write(padRight(header.getFileVersion(), 8));
        writer.write(NEW_LINE);
        writer.write(padRight(header.getTaNo(), 20));
        writer.write(NEW_LINE);
        writer.write(padRight(header.getFileReceiver(), 20));
        writer.write(NEW_LINE);
        writer.write(padRight(header.getDate(), 8));
        writer.write(NEW_LINE);
        writer.write(padRight(header.getFileNo(), 8));
        writer.write(NEW_LINE);
        writer.write(padRight(header.getFileType(), 8));
        writer.write(NEW_LINE);
        writer.write(padRight(header.getDataSender(), 8));
        writer.write(NEW_LINE);
        writer.write(padRight(header.getDataReceiver(), 8));
        writer.write(NEW_LINE);
        writer.write(padLeft(String.valueOf(fieldNameList.size()), 8, '0'));
        writer.write(NEW_LINE);
        
        for (String fieldName : fieldNameList) {
            writer.write(fieldName);
            writer.write(NEW_LINE);
        }
    }
    
    /**
     * 写入文件数据
     */
    private void writeFileDatas(BufferedWriter writer, List<OFDFieldInfo> fieldInfos, List<Object[]> datas) 
            throws IOException {
        writer.write(padLeft(String.valueOf(datas.size()), 16, '0'));
        writer.write(NEW_LINE);
        
        int rowSize = fieldInfos.stream().mapToInt(OFDFieldInfo::getFieldSize).sum();
        byte[] blankRow = new byte[rowSize];
        Arrays.fill(blankRow, (byte) 32); // 空格
        
        for (Object[] data : datas) {
            byte[] rowTemp = Arrays.copyOf(blankRow, rowSize);
            int index = 0;
            
            for (int i = 0; i < fieldInfos.size(); i++) {
                OFDFieldInfo fieldInfo = fieldInfos.get(i);
                
                if (fieldInfo.getFieldDataType() == String.class) {
                    // 字符串类型
                    String value = data[i] == null ? "" : data[i].toString();
                    if (!value.isEmpty()) {
                        byte[] bytes = value.getBytes(GB_ENCODING);
                        System.arraycopy(bytes, 0, rowTemp, index, 
                            Math.min(bytes.length, fieldInfo.getFieldSize()));
                    }
                } else {
                    // 数字类型
                    if (data[i] != null) {
                        String value;
                        if (fieldInfo.getFieldSize() > 16 || !(data[i] instanceof BigDecimal)) {
                            // 超长数字或非BigDecimal，直接转字符串
                            value = data[i].toString().replace(".", "");
                            value = padLeft(value, fieldInfo.getFieldSize(), '0');
                        } else {
                            // BigDecimal处理
                            BigDecimal tmpFieldData = (BigDecimal) data[i];
                            //for (int times = 0; times < fieldInfo.getFieldSize2(); times++) {
                            //    tmpFieldData = tmpFieldData.multiply(BigDecimal.TEN);
                            //}
                            tmpFieldData = tmpFieldData.movePointRight(fieldInfo.getFieldSize2());
                            long longData = tmpFieldData.longValue();
                            value = padLeft(String.valueOf(longData), fieldInfo.getFieldSize(), '0');
                        }
                        byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
                        System.arraycopy(bytes, 0, rowTemp, index, 
                            Math.min(bytes.length, fieldInfo.getFieldSize()));
                    }
                }
                
                index += fieldInfo.getFieldSize();
            }
            
            writer.write(new String(rowTemp, GB_ENCODING));
            writer.write(NEW_LINE);
        }
        
        writer.write("OFDCFEND");
        writer.write(NEW_LINE);
    }
    
    /**
     * 右填充
     */
    private String padRight(String str, int length) {
        if (str == null) str = "";
        if (str.length() >= length) return str;
        return String.format("%-" + length + "s", str);
    }
    
    /**
     * 左填充
     */
    private String padLeft(String str, int length, char padChar) {
        if (str == null) str = "";
        if (str.length() >= length) return str;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length - str.length(); i++) {
            sb.append(padChar);
        }
        sb.append(str);
        return sb.toString();
    }
}
