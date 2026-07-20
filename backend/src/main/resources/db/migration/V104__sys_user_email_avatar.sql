-- 为 sys_user 增补 email / avatar 列。
-- 此前 AuthService.getUserInfo() 因表无此列恒返回 null；前端个人资料弹窗已就绪等待消费。
-- 仅补展示字段，不改用户 CRUD 表单（最小闭环）。
ALTER TABLE sys_user ADD COLUMN email VARCHAR(128) NULL;
ALTER TABLE sys_user ADD COLUMN avatar VARCHAR(512) NULL;

-- 给内置管理员补一个可见的邮箱，便于个人资料弹窗展示真实数据（avatar 留空走用户名首字母兜底）。
UPDATE sys_user
SET email = 'admin@tuowei.local'
WHERE username = 'admin' AND email IS NULL;
