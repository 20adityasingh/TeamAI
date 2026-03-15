package com.distributed.teamai.workspace_service.mapper;

import com.distributed.teamai.common_lib.dto.FileNode;
import com.distributed.teamai.workspace_service.entity.ProjectFile;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ProjectFileMapper {

    List<FileNode> toFileNodeList(List<ProjectFile> projectFiles);

}
