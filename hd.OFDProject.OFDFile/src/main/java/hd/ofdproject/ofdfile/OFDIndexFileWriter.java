package hd.ofdproject.ofdfile;

import java.io.*;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 中登索引文件写入器
 */
public class OFDIndexFileWriter {
    
    private static final Charset GB_ENCODING = Charset.forName("GB18030");
    private static final String NEW_LINE = "\r\n";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    
    /**
     * 创建索引文件，fileNames会自动排序
     * 
     * @param fileVersion 文件版本
     * @param fileCreator 文件创建者
     * @param fileReceiver 文件接收者
     * @param date 日期
     * @param fileNames 文件名列表
     * @return 索引文件字节数组
     * @throws IOException IO异常
     */
    public static byte[] createFile(String fileVersion, String fileCreator, String fileReceiver, 
                                     LocalDate date, List<String> fileNames) throws IOException {
        
        try (ByteArrayOutputStream ms = new ByteArrayOutputStream();
             OutputStreamWriter osw = new OutputStreamWriter(ms, GB_ENCODING);
             BufferedWriter writer = new BufferedWriter(osw)) {
            
            writer.write("OFDCFIDX");
            writer.write(NEW_LINE);
            writer.write(padRight(fileVersion, 8));
            writer.write(NEW_LINE);
            writer.write(padRight(fileCreator, 20));
            writer.write(NEW_LINE);
            writer.write(padRight(fileReceiver, 20));
            writer.write(NEW_LINE);
            writer.write(padRight(date.format(DATE_FORMATTER), 8));
            writer.write(NEW_LINE);
            writer.write(padLeft(String.valueOf(fileNames.size()), 8, '0'));
            writer.write(NEW_LINE);
            
            // 文件名排序后写入
            List<String> sortedFileNames = fileNames.stream()
                .sorted()
                .collect(Collectors.toList());
            
            for (String fileName : sortedFileNames) {
                writer.write(fileName);
                writer.write(NEW_LINE);
            }
            
            writer.write("OFDCFEND");
            writer.write(NEW_LINE);
            
            writer.flush();
            return ms.toByteArray();
        }
    }
    
    /**
     * 创建索引文件（使用字符串日期）
     * 
     * @param fileVersion 文件版本
     * @param fileCreator 文件创建者
     * @param fileReceiver 文件接收者
     * @param dateStr 日期字符串（yyyyMMdd格式）
     * @param fileNames 文件名列表
     * @return 索引文件字节数组
     * @throws IOException IO异常
     */
    public static byte[] createFile(String fileVersion, String fileCreator, String fileReceiver, 
                                     String dateStr, List<String> fileNames) throws IOException {
        LocalDate date = LocalDate.parse(dateStr, DATE_FORMATTER);
        return createFile(fileVersion, fileCreator, fileReceiver, date, fileNames);
    }
    
    /**
     * 右填充
     */
    private static String padRight(String str, int length) {
        if (str == null) str = "";
        if (str.length() >= length) return str;
        return String.format("%-" + length + "s", str);
    }
    
    /**
     * 左填充
     */
    private static String padLeft(String str, int length, char padChar) {
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
