package com.example.ninjaattack.model.domain;

// 使用 Java Record 来简化坐标类
// 如果您使用的 Java 版本低于 16,
// 请将其改为一个标准的 class:
/*
import lombok.Data;
@Data
public class Point {
    private final int r;
    private final int c;
}
*/

public record Point(int r, int c) {
}