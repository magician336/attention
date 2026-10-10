# CodeGraph 工具

项目已注册 CodeGraph：

- 索引：`.codegraph/`
- MCP 工具：`codegraph_explore`
- 项目路径：`.`（当前工作树根目录）
- 更新索引：`codegraph update`

使用约定：涉及代码理解、定位、调用链、影响范围或编辑前调查时，先通过 MCP 调用 `codegraph_explore`。索引数据库和临时文件保持本地，不提交 `.codegraph/` 中除 `.gitignore` 外的文件。
