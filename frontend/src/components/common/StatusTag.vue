<template>
  <el-tag
    :type="tagType"
    :effect="effect"
    :size="size"
    :round="round"
    :class="['status-tag', `status-${status.toLowerCase()}`]"
  >
    <span class="status-dot"></span>
    <span class="status-text">{{ text || statusText }}</span>
  </el-tag>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useAppStore } from '@/store/modules/app'

interface Props {
  status: string
  text?: string
  type?: 'primary' | 'success' | 'warning' | 'danger' | 'info' | 'default'
  effect?: 'light' | 'dark' | 'plain'
  size?: 'large' | 'default' | 'small'
  round?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  effect: 'light',
  size: 'default',
  round: true
})

const appStore = useAppStore()

// 状态文本映射
const statusTextMapZh: Record<string, string> = {
  // 通用状态
  'ACTIVE': '启用',
  'INACTIVE': '停用',
  'DISABLED': '停用',
  'DRAFT': '草稿',
  'PENDING': '待审核',
  'APPROVED': '已审核',
  'REJECTED': '已驳回',
  'COMPLETED': '已完成',
  'CANCELLED': '已取消',

  // 订单状态
  'DELIVERING': '配送中',
  'IN_PRODUCTION': '生产中',
  'PLANNED': '已计划',
  'IN_TRANSIT': '运输中',

  // 库存状态
  'CHECKING': '盘点中',
  'OUT_OF_STOCK': '缺货',
  'LOW_STOCK': '库存不足',
  'OVER_STOCK': '库存过量',

  // 财务状态
  'UNPAID': '未支付',
  'PARTIAL': '部分支付',
  'PAID': '已支付',
  'OVERDUE': '已逾期',
  'POSTED': '已过账',

  // 资产状态
  'ASSET': '资产',
  'LIABILITY': '负债',
  'EQUITY': '所有者权益',
  'REVENUE': '收入',
  'EXPENSE': '费用',

  // 收付款方式
  'CASH': '现金',
  'BANK_TRANSFER': '银行转账',
  'CHECK': '支票',
  'OTHER': '其他',

  // 凭证类型
  'RECEIPT': '收款',
  'PAYMENT': '付款',
  'TRANSFER': '转账',
  'ADJUST': '调整',

  // 库存类型
  'GAIN': '盘盈',
  'LOSS': '盘亏',

  // 仓库类型
  'RAW': '原材料',
  'PRODUCT': '成品',
  'SEMI': '半成品',

  // 客户类型
  'COMPANY': '企业',
  'INDIVIDUAL': '个人',

  // 预警状态
  'RESOLVED': '已解决',
  'IGNORED': '已忽略',

  // 日志状态
  'SUCCESS': '成功',
  'FAIL': '失败',

  // 菜单类型
  'MENU': '菜单',
  'BUTTON': '按钮',

  // 用户状态
  'LOCKED': '已锁定'
}

const statusTextMapEn: Record<string, string> = {
  'ACTIVE': 'Active',
  'INACTIVE': 'Inactive',
  'DISABLED': 'Inactive',
  'DRAFT': 'Draft',
  'PENDING': 'Pending',
  'APPROVED': 'Approved',
  'REJECTED': 'Rejected',
  'COMPLETED': 'Completed',
  'CANCELLED': 'Cancelled',
  'DELIVERING': 'Delivering',
  'IN_PRODUCTION': 'In production',
  'PLANNED': 'Planned',
  'IN_TRANSIT': 'In transit',
  'CHECKING': 'Checking',
  'OUT_OF_STOCK': 'Out of stock',
  'LOW_STOCK': 'Low stock',
  'OVER_STOCK': 'Overstock',
  'UNPAID': 'Unpaid',
  'PARTIAL': 'Partial',
  'PAID': 'Paid',
  'OVERDUE': 'Overdue',
  'POSTED': 'Posted',
  'ASSET': 'Asset',
  'LIABILITY': 'Liability',
  'EQUITY': 'Equity',
  'REVENUE': 'Revenue',
  'EXPENSE': 'Expense',
  'CASH': 'Cash',
  'BANK_TRANSFER': 'Bank transfer',
  'CHECK': 'Check',
  'OTHER': 'Other',
  'RECEIPT': 'Receipt',
  'PAYMENT': 'Payment',
  'TRANSFER': 'Transfer',
  'ADJUST': 'Adjust',
  'GAIN': 'Gain',
  'LOSS': 'Loss',
  'RAW': 'Raw material',
  'PRODUCT': 'Finished goods',
  'SEMI': 'Semi-finished',
  'COMPANY': 'Company',
  'INDIVIDUAL': 'Individual',
  'RESOLVED': 'Resolved',
  'IGNORED': 'Ignored',
  'SUCCESS': 'Success',
  'FAIL': 'Failed',
  'MENU': 'Menu',
  'BUTTON': 'Button',
  'LOCKED': 'Locked'
}

const statusTextMap = computed(() => (
  appStore.locale === 'en-US' ? statusTextMapEn : statusTextMapZh
))

const statusText = computed(() => statusTextMap.value[props.status] || props.status)

// 状态颜色映射
type TagType = 'primary' | 'success' | 'warning' | 'danger' | 'info' | 'default'
type ElementTagType = Exclude<TagType, 'default'>

const statusTypeMap: Record<string, TagType> = {
  'ACTIVE': 'success',
  'APPROVED': 'success',
  'COMPLETED': 'success',
  'POSTED': 'success',
  'PAID': 'success',
  'SUCCESS': 'success',
  'RESOLVED': 'success',

  'PENDING': 'warning',
  'DRAFT': 'info',
  'CHECKING': 'warning',
  'PLANNED': 'warning',
  'IN_PRODUCTION': 'warning',
  'DELIVERING': 'warning',
  'IN_TRANSIT': 'warning',
  'PARTIAL': 'warning',
  'UNPAID': 'warning',
  'LOW_STOCK': 'warning',

  'REJECTED': 'danger',
  'CANCELLED': 'danger',
  'INACTIVE': 'danger',
  'DISABLED': 'danger',
  'FAIL': 'danger',
  'OVERDUE': 'danger',
  'OUT_OF_STOCK': 'danger',
  'OVER_STOCK': 'danger',
  'LOCKED': 'danger',

  'IGNORED': 'info'
}

const tagType = computed<ElementTagType | undefined>(() => {
  const type = props.type || statusTypeMap[props.status] || 'default'
  return type === 'default' ? undefined : type
})
</script>

<style scoped>
.status-tag {
  font-family: 'Plus Jakarta Sans', 'Segoe UI', system-ui, -apple-system, sans-serif;
  font-weight: 500;
  letter-spacing: 0.3px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 12px;
  transition: all 0.2s ease;
  border: none;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
}

.status-tag:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 8px rgba(0, 0, 0, 0.1);
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  animation: pulse 2s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% {
    opacity: 1;
    transform: scale(1);
  }
  50% {
    opacity: 0.7;
    transform: scale(1.2);
  }
}

/* Success状态 */
.status-tag.el-tag--success.el-tag--light .status-dot {
  background: #52c41a;
  box-shadow: 0 0 8px rgba(82, 196, 26, 0.5);
}

/* Warning状态 */
.status-tag.el-tag--warning.el-tag--light .status-dot {
  background: #faad14;
  box-shadow: 0 0 8px rgba(250, 173, 20, 0.5);
}

/* Danger状态 */
.status-tag.el-tag--danger.el-tag--light .status-dot {
  background: #ff4d4f;
  box-shadow: 0 0 8px rgba(255, 77, 79, 0.5);
}

/* Info状态 */
.status-tag.el-tag--info.el-tag--light .status-dot {
  background: #1890ff;
  box-shadow: 0 0 8px rgba(24, 144, 255, 0.5);
}

.status-text {
  font-size: 12px;
}
</style>
