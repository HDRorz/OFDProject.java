package hd.ofdproject.ofdfile;

import lombok.Data;

/**
 * 中登文件头信息
 */
@Data
public class OFDFileHeader {
    
    /**
     * 文件版本
     */
    private String fileVersion;
    
    /**
     * TA代码
     */
    private String taNo;
    
    /**
     * 文件接收方
     */
    private String fileReceiver;
    
    /**
     * 日期
     */
    private String date;
    
    /**
     * 文件编号
     */
    private String fileNo;
    
    /**
     * 文件类型
     */
    private String fileType;
    
    /**
     * 数据发送方
     */
    private String dataSender;
    
    /**
     * 数据接收方
     */
    private String dataReceiver;
    
    /**
     * 文件名
     */
    private String fileName;
}
