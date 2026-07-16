package hd.ofdproject.ofdfile;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 中登文件字段信息
 */
@Data
public class OFDFieldInfo {
    
    public static final int STRING_MAX_LENGTH = 4000;
    
    /**
     * 字段名称
     */
    private String fieldName;
    
    /**
     * 字段类型
     */
    private String fieldType;
    
    /**
     * 字段大小
     */
    private int fieldSize;
    
    /**
     * 字段小数位数
     */
    private int fieldSize2;
    
    /**
     * 字段数据类型
     */
    private Class<?> fieldDataType;
    
    /**
     * 字段描述
     */
    private String fieldDesc;
    
    public OFDFieldInfo(String name, String fieldType, String fieldSize, String fieldSize2) {
        this(name, fieldType, fieldSize, fieldSize2, "");
    }
    
    public OFDFieldInfo(String name, String fieldType, String fieldSize, String fieldSize2, String fieldDesc) {
        this.fieldName = name;
        this.fieldType = fieldType;
        
        if ("TEXT".equals(fieldSize)) {
            this.fieldSize = STRING_MAX_LENGTH;
        } else {
            this.fieldSize = Integer.parseInt(fieldSize);
        }
        
        if (fieldSize2 == null || fieldSize2.isEmpty()) {
            this.fieldSize2 = 0;
        } else {
            this.fieldSize2 = Integer.parseInt(fieldSize2);
        }
        
        this.fieldDataType = getDataType();
        this.fieldDesc = fieldDesc;
    }
    
    private Class<?> getDataType() {
        switch (fieldType) {
            case "N":
                return BigDecimal.class;
            case "A":
            case "C":
            case "TEXT":
            default:
                return String.class;
        }
    }
}
