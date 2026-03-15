package com.distributed.teamai.common_lib.dto;

public record FileNode(String path) {
    @Override
    public String toString() {
        return path;
    }
}
