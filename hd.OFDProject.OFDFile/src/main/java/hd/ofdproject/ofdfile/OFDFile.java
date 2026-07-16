package hd.ofdproject.ofdfile;

import lombok.Getter;

import java.util.*;
import java.util.function.BiFunction;

/**
 * 中登文件数据
 */
@Getter
public class OFDFile {
    
    /**
     * 文件头信息
     */
    private final OFDFileHeader fileHeader;
    
    /**
     * 字段数量
     */
    private final int fieldCount;
    
    /**
     * 文件字段
     */
    private final List<OFDFieldInfo> fieldInfos;
    
    /**
     * 字段名称到索引的映射
     */
    private final Map<String, Integer> fieldIndexDict = new HashMap<>();
    
    /**
     * 数据行数
     */
    private final int rowCount;
    
    /**
     * 文件原始数据（字节）
     */
    private final List<byte[]> rawDatas;
    
    /**
     * 文件数据（延迟加载）
     */
    private volatile List<Object[]> datas;
    
    /**
     * 反序列化行数据的函数
     */
    private BiFunction<List<OFDFieldInfo>, byte[], Object[]> deserializeRowDataFunc = OFDFileReader::deserializeRowData;
    
    public OFDFile(OFDFileHeader header, List<OFDFieldInfo> fieldInfos, List<byte[]> datas) {
        this.fileHeader = header;
        this.fieldInfos = fieldInfos;
        this.fieldCount = fieldInfos.size();
        this.rawDatas = datas;
        this.rowCount = datas.size();
        
        // 初始化字段索引字典
        for (int i = 0; i < fieldCount; i++) {
            OFDFieldInfo fi = fieldInfos.get(i);
            fieldIndexDict.put(fi.getFieldName(), i);
            fieldIndexDict.put(fi.getFieldName().toLowerCase(), i);
            fieldIndexDict.put(fi.getFieldName().toUpperCase(), i);
        }
    }
    
    /**
     * 获取数据（延迟加载）
     */
    public List<Object[]> getDatas() {
        if (datas == null) {
            synchronized (this) {
                if (datas == null) {
                    datas = new ArrayList<>(rowCount);
                    int i = 0;
                    try {
                        for (i = 0; i < rawDatas.size(); i++) {
                            datas.add(deserializeRowDataFunc.apply(fieldInfos, rawDatas.get(i)));
                        }
                    } catch (Exception e) {
                        int rownum = 10 + 1 + fieldCount + 1 + i;
                        throw new RuntimeException("第" + rownum + "行异常，" + e.getMessage(), e);
                    }
                }
            }
        }
        return datas;
    }
    
    /**
     * 设置反序列化函数
     */
    public void setDeserializeRowDataFunc(BiFunction<List<OFDFieldInfo>, byte[], Object[]> func) {
        this.deserializeRowDataFunc = func;
    }
    
    /**
     * 获取某个字段的顺序
     */
    public int getFieldIndex(String fieldName) {
        Integer index = fieldIndexDict.get(fieldName);
        if (index != null) {
            return index;
        }
        
        index = fieldIndexDict.get(fieldName.toLowerCase());
        if (index != null) {
            return index;
        }
        
        throw new IllegalArgumentException("文件不包含该字段: " + fieldName);
    }
    
    /**
     * 获取一行中某个字段值
     */
    @SuppressWarnings("unchecked")
    public <T> T getFieldData(Object[] row, String fieldName) {
        try {
            int index = getFieldIndex(fieldName);
            return (T) row[index];
        } catch (Exception e) {
            // 返回默认值
            return null;
        }
    }
    
    /**
     * 获取一行中某个字段值（带默认值）
     */
    @SuppressWarnings("unchecked")
    public <T> T getFieldData(Object[] row, String fieldName, T defaultValue) {
        try {
            int index = getFieldIndex(fieldName);
            Object value = row[index];
            return value != null ? (T) value : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }
    
    /**
     * 将一行转成字典
     */
    public Map<String, Object> getOneRow(Object[] row) {
        Map<String, Object> retDict = new HashMap<>();
        for (int i = 0; i < fieldCount; i++) {
            retDict.put(fieldInfos.get(i).getFieldName(), row[i]);
        }
        return retDict;
    }
}
