# hd.ofdproject.ofdfile - 中登文件处理库

本模块是从 C# 项目 `OFDFile.IO` 迁移到 Java 的中登文件处理库。

## 功能概述

提供中登文件的读取和写入功能，支持多种文件版本（V21、V22等）。


## 使用示例

### 读取文件
```java
OFDFileReader reader = new OFDFileReader();
OFDFile fileData = reader.readFile("path/to/zdfile.txt");

// 获取文件头信息
OFDFileHeader header = fileData.getFileHeader();
System.out.println("文件版本: " + header.getFileVersion());

// 获取数据
List<Object[]> datas = fileData.getDatas();
for (Object[] row : datas) {
    // 处理每行数据
}

// 获取某个字段的值
String value = fileData.getFieldData(row, "字段名", "默认值");
```

### 写入文件
```java
OFDFileWriter writer = new OFDFileWriter();

// 准备文件头
OFDFileHeader header = new OFDFileHeader();
header.setFileVersion("22");
header.setTaNo("TA123456");
// ... 设置其他属性

// 准备字段列表
List<String> fieldNames = Arrays.asList("字段1", "字段2", "字段3");

// 准备数据
List<Object[]> datas = new ArrayList<>();
datas.add(new Object[]{"值1", "值2", BigDecimal.valueOf(100.00)});

// 生成文件
byte[] fileBytes = writer.createFile(header, fieldNames, datas);

// 写入磁盘
Files.write(Paths.get("output.txt"), fileBytes);
```

### 快速读取大文件
```java
OFDFileFastReader fastReader = new OFDFileFastReader();
OFDFile fileData = fastReader.readFile("path/to/large_zdfile.txt");

// 数据会使用优化的反序列化方法自动加载
List<Object[]> datas = fileData.getDatas();
```

### 创建索引文件
```java
List<String> fileNames = Arrays.asList("file1.txt", "file2.txt", "file3.txt");

byte[] indexBytes = OFDIndexFileWriter.createFile(
    "22",                    // 文件版本
    "CREATOR001",            // 创建者
    "RECEIVER001",           // 接收者
    LocalDate.now(),         // 日期
    fileNames                // 文件名列表（会自动排序）
);

// 写入磁盘
Files.write(Paths.get("index.idx"), indexBytes);
```

## 配置文件

资源目录下的配置文件不提供

## 依赖

- Lombok: 简化 POJO 类的 getter/setter
- JDK 17+

