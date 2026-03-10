# 基于知识图谱与大语言模型的中药智能处方推荐系统

## 项目简介

本项目是一个融合**知识图谱**与**大语言模型**的中药智能处方推荐系统，旨在为基层医生提供高效、安全的辅助诊疗工具。系统以中医“辨证论治”为核心思想，通过构建中医药知识图谱（涵盖药材、方剂、症状、证型等实体及其语义关系），并结合大语言模型的自然语言理解与生成能力，实现从症状输入到处方推荐、配伍禁忌检测、病历导出的一站式智能化服务。

系统采用前后端分离架构，后端基于 Spring Boot 提供 RESTful API，前端使用 Vue 3 构建动态交互界面，知识图谱存储选用 Neo4j，事务性数据持久化采用 MySQL。项目已通过系统性功能测试与性能验证，具备良好的可扩展性与可维护性。

## 主要功能

### 医生端
- **智能问诊对话**：支持自然语言症状输入，系统通过知识图谱推理与大语言模型生成辨证分析与处方建议，流式输出结果，保留完整对话上下文。
- **处方推荐与配伍检测**：基于症状-证型-方剂-药材的多层映射，生成个性化处方，并自动检测“十八反”“十九畏”等配伍禁忌，提供安全替代建议。
- **病历自动处理**：支持上传 PDF/DOCX/TXT 格式病历文件，自动解析症状与诊断信息，快速生成处方并支持导出为 PDF。
- **历史记录与反馈**：查看历史问诊记录，修改推荐结果并反馈准确性，为模型优化提供数据支撑。
- **报告打印与导出**：支持在线预览、编辑病历内容，并导出为规范化的 PDF 文件，便于存档与打印。

### 管理员端
- **用户管理**：创建、修改、删除医生账户，维护系统用户权限。
- **知识图谱维护**：更新中药、症状、方剂等实体及其关系数据，完善知识图谱结构。
- **模型与接口管理**：配置大语言模型 API 接口，监控调用状态与性能。
- **数据统计与日志管理**：监控系统运行情况、用户访问记录及接口调用日志，便于运维与问题排查。

## 技术栈

| 层次         | 技术选型                                                                 |
|--------------|--------------------------------------------------------------------------|
| 前端         | Vue 3 + Vue Router + Pinia + Element Plus + Axios + ECharts             |
| 后端         | Java 17 + Spring Boot 3 + Spring Security + JWT + MyBatis               |
| 知识图谱     | Neo4j + Cypher + py2neo (数据处理)                                      |
| 数据库       | MySQL 8.0 + HikariCP 连接池                                             |
| 大语言模型   | DeepSeek API (可替换为其他模型) + 自定义提示词工程                      |
| 自然语言处理 | HanLP (实体识别) + 文本清洗与格式化模块                                 |
| 文件处理     | PDFBox + Apache POI + 自定义解析引擎                                    |
| 构建工具     | Maven (后端) + NPM (前端)                                                |
| 接口文档     | Swagger / Knife4j (可选)                                                 |
| 开发工具     | IntelliJ IDEA + WebStorm + Git                                           |

## 快速开始

### 环境要求
- JDK 17+
- Node.js 18+ 与 NPM
- MySQL 8.0
- Neo4j 4.4+ (社区版即可)
- Maven 3.6+

### 安装步骤

1. **克隆仓库**
   ```bash
   ```

2. **后端配置与启动**
   - 创建 MySQL 数据库（如 `tcm_db`），执行 `sql/` 目录下的初始化脚本创建表结构。
   - 安装并启动 Neo4j 数据库，创建图谱数据库（如 `tcm_graph`）。
   - 修改后端配置文件 `src/main/resources/application.yml`，配置 MySQL、Neo4j 连接信息以及大语言模型 API 密钥。
   - 使用 Maven 打包并运行：
     ```bash
     cd backend
     mvn clean install
     mvn spring-boot:run
     ```
   - 后端默认运行在 `http://localhost:8080`，API 文档访问 `http://localhost:8080/swagger-ui.html`（若集成）。

3. **前端配置与启动**
   - 进入前端目录，安装依赖：
     ```bash
     cd frontend
     npm install
     ```
   - 修改接口地址（如 `.env.development` 文件中的 `VITE_API_BASE_URL`）。
   - 启动开发服务器：
     ```bash
     npm run dev
     ```
   - 前端默认运行在 `http://localhost:5173`，浏览器打开即可访问。

4. **知识图谱数据导入**
   - 使用 Python 脚本（位于 `scripts/` 目录）处理原始 CSV 数据，并导入 Neo4j：
     ```bash
     pip install pandas py2neo
     python scripts/import_nodes.py
     python scripts/import_relations.py
     ```

5. **默认账户**
   - 管理员：admin / 123456（需提前插入数据）
   - 医生：doctor / 123456

## 项目结构

```
tcm-intelligent-prescription/
├── backend/                     # 后端源码
│   ├── src/main/java/...        # 业务代码 (controller, service, mapper, entity)
│   ├── src/main/resources/      # 配置文件、Mapper XML
│   └── pom.xml                  # Maven 依赖
├── frontend/                    # 前端源码
│   ├── src/
│   │   ├── api/                 # API 请求封装
│   │   ├── views/               # 页面组件
│   │   ├── router/              # 路由配置
│   │   ├── store/               # Pinia 状态管理
│   │   └── ...
│   ├── package.json             # NPM 依赖
│   └── vite.config.js           # Vite 配置
├── scripts/                      # 数据处理脚本 (Python)
│   ├── import_nodes.py          # 节点导入
│   └── import_relations.py      # 关系导入
├── sql/                          # 数据库初始化脚本
└── README.md
```

## 主要设计亮点

- **知识约束生成**：以知识图谱作为结构化领域知识的“锚点”，引导并约束大语言模型的生成过程，确保推荐结果符合中医药学逻辑，提升语义理解深度与专业一致性。
- **推理与安全并重**：处方生成后必须通过配伍禁忌检测关卡，主动提供修正建议，将智能化服务从“推荐”拓展至“推荐与安全保障”。
- **模块化解耦架构**：前后端分离，知识图谱、大语言模型、业务应用层相对独立，便于知识库迭代与模型升级。
- **可解释性设计**：系统输出包含知识图谱命中状态、证据说明与注意事项，确保推荐结果可追溯、可解释。

## 测试结果摘要

- **功能测试**：覆盖患者信息管理、智能对话、文件上传、处方生成等核心流程，正常场景运行稳定，异常处理与状态控制需进一步优化。
- **性能测试**：单用户场景响应及时，首次图数据库调用存在预热成本，复杂多跳查询响应时间有待优化。
- **RAG vs 知识图谱对比**：RAG 模式在功能准确性（领先约6.6分）与响应速度（快约72.3秒）上均表现更优，适合作为默认推荐路径；知识图谱直连模式可作为证据回溯与降级回退的辅助手段。

## 贡献指南

欢迎提交 Issue 或 Pull Request。请确保代码风格符合项目规范，并补充必要的测试用例。

## 许可证

本项目仅供学习交流使用，未经许可不得用于商业用途。

---

**项目状态**：课程设计完成，功能闭环可演示。后续可扩展轻量化专业模型、优化图谱查询性能、推动临床试点验证等方向。



<img width="692" height="382" alt="image" src="https://github.com/user-attachments/assets/ce1e66f4-0120-46c2-8d0a-e7b4aed68f56" />
<img width="692" height="399" alt="image" src="https://github.com/user-attachments/assets/e93dc807-9fab-4c7b-b8a5-b0d70c32006b" />
<img width="697" height="376" alt="image" src="https://github.com/user-attachments/assets/98febe10-9d5d-49cb-9662-1fb7efb5fcaa" />
<img width="683" height="364" alt="image" src="https://github.com/user-attachments/assets/6fa2126d-1ba9-4ee7-a91e-003e763aa741" />
