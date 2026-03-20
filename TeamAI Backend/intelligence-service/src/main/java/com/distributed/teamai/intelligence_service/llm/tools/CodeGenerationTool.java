package com.distributed.teamai.intelligence_service.llm.tools;

import com.distributed.teamai.intelligence_service.client.WorkspaceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@Slf4j
public class CodeGenerationTool {

    private final WorkspaceClient workspaceClient;
    private final Long projectId;

    @Tool(
            name = "read_files",
            description = "Read the content of files. Only input the file names present inside the FILE_TREE. DO NOT input any path which is not present under the FILE_TREE."
    )
    public List<String> readFiles(
            @ToolParam(description = "List of relative paths (e.g., ['src/App.tsx'])")
            List<String> paths
    ){
        log.info("Executing readFiles tool for Project ID: {} with paths: {}", projectId, paths);
        List<String> result = new ArrayList<>();

        for(String path: paths){
            try {
                String cleanPath = path.startsWith("/")? path.substring(1) : path;
                String content  = workspaceClient.getFileContent(projectId, cleanPath);
                result.add(
                        String.format(
                                "---START OF FILE: %s---\n%s\n---END OF FILE---", cleanPath, content
                        )
                );
            } catch (Exception e) {
                log.error("Error reading file {}: {}", path, e.getMessage());
                result.add(String.format("---ERROR READING FILE: %s---\nFile not found or access denied.", path));
            }
        }

        return result;

    }
}
