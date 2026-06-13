import re

with open("judicial-appraisal-backend/src/main/java/com/example/judicialappraisal/platform/service/JudicialConfigImportService.java", "r") as f:
    content = f.read()

nodes_replacement = """                node("START", "开始", "start", "single", null, 0, 0, false, null, 0),
                node("ASSISTANT_DRAFT", "项目辅助人编制不予受理通知书", "task", "candidate", "项目辅助人", 1, 24, true, workflow.formCode(), 10),
                node("PROJECT_REVIEW", "项目负责人审核不予受理通知书", "task", "candidate", "项目负责人", 1, 48, true, workflow.formCode(), 20),
                node("ARCHIVIST_CONFIRM", "档案管理员同意盖章", "task", "candidate", "档案管理员", 1, 24, true, workflow.formCode(), 30),
                node("SEAL_APPLICATION", "档案管理员提交用章申请", "task", "candidate", "档案管理员", 1, 24, true, "seal-application", 40),
                node("SEALED_NOTICE_UPLOAD", "项目辅助人上传盖章扫描版", "task", "candidate", "项目辅助人", 1, 48, true, workflow.formCode(), 50),
                node("ARCHIVE_SUBFLOW", "进入归档子流程", "task", "candidate", "档案管理员", 1, 24, true, "archive", 60),
                node("END", "流程结束", "end", "single", null, 0, 0, false, null, 70)
        );"""

transitions_replacement = """        List<WorkflowTransitionRequest> transitions = List.of(
                transition("START", "ASSISTANT_DRAFT", "APPROVE", "进入不予受理", null, 0, 10),
                transition("ASSISTANT_DRAFT", "PROJECT_REVIEW", "APPROVE", "转交项目负责人审核", null, 1, 20),
                transition("PROJECT_REVIEW", "ARCHIVIST_CONFIRM", "APPROVE", "审核通过，转档案管理员盖章", "form.projectReviewPassed == true", 1, 30),
                transition("PROJECT_REVIEW", "ASSISTANT_DRAFT", "RETURN", "退回项目辅助人修改通知书", "form.projectReviewPassed == false", 1, 31),
                transition("ARCHIVIST_CONFIRM", "SEAL_APPLICATION", "APPROVE", "同意盖章并提交申请", "form.sealRequired == true", 1, 40,
                        subflowConfig("seal-application", "审核通过后自动进入用章流程")),
                transition("ARCHIVIST_CONFIRM", "SEALED_NOTICE_UPLOAD", "APPROVE", "无需盖章直接上传", "form.sealRequired == false", 1, 41),
                transition("ARCHIVIST_CONFIRM", "PROJECT_REVIEW", "RETURN", "退回项目负责人复核", null, 1, 42),
                transition("SEAL_APPLICATION", "SEALED_NOTICE_UPLOAD", "COMPLETE", "用章流程完成", null, 1, 50),
                transition("SEALED_NOTICE_UPLOAD", "ARCHIVE_SUBFLOW", "APPROVE", "上传扫描版并确认送达，进入归档", "form.archiveConfirmed == true", 1, 60,
                        subflowConfig("archive", "不予受理通知书送达后自动进入归档流程")),
                transition("SEALED_NOTICE_UPLOAD", "ARCHIVIST_CONFIRM", "RETURN", "退回档案管理员重新确认盖章", null, 1, 61),
                transition("ARCHIVE_SUBFLOW", "END", "COMPLETE", "归档子流程已触发", null, 1, 70)
        );"""

# Replace nodes
content = re.sub(r'node\("START", "开始", "start", "single", null, 0, 0, false, null, 0\),\n.*node\("END", "流程结束", "end", "single", null, 0, 0, false, null, 70\)\n\s*\);', nodes_replacement, content, flags=re.DOTALL)

# Replace transitions
content = re.sub(r'List<WorkflowTransitionRequest> transitions = List.of\(\n\s*transition\("START", "ASSISTANT_DRAFT", "APPROVE", "进入不予受理", null, 0, 10\),.*transition\("ARCHIVE", "END", "COMPLETE", "归档子流程已触发", null, 1, 70\)\n\s*\);', transitions_replacement, content, flags=re.DOTALL)

with open("judicial-appraisal-backend/src/main/java/com/example/judicialappraisal/platform/service/JudicialConfigImportService.java", "w") as f:
    f.write(content)
