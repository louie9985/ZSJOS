<template>
  <Dialog v-model="dialogVisible" title="资产详情" width="820px">
    <div v-loading="loading">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="资产编号">{{ detail.assetCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="资产名称">{{ detail.name || '-' }}</el-descriptions-item>
        <el-descriptions-item label="分类">{{ detail.categoryName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="管理模式">
          {{ detail.managementMode === 2 ? '批量管理' : '单件管理' }}
        </el-descriptions-item>
        <el-descriptions-item label="数量">
          {{ detail.quantity || 1 }} {{ detail.unit || '个' }}
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <dict-tag
            v-if="detail.status != null"
            :type="'eam_asset_status'"
            :value="detail.status"
          />
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="品牌型号">{{ detail.brand || '-' }}</el-descriptions-item>
        <el-descriptions-item label="规格参数">
          {{ detail.specification || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="序列号">{{ detail.sn || '-' }}</el-descriptions-item>
        <el-descriptions-item label="条码">{{ detail.barcode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="购入日期">
          {{ detail.purchaseDate || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="使用部门">
          {{ detail.useDeptName || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="使用人">{{ detail.useUserName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="使用人姓名快照">{{ detail.useUserNameSnapshot || '-' }}</el-descriptions-item>
        <el-descriptions-item label="资产来源">{{ detail.sourceLabelSnapshot || '-' }}</el-descriptions-item>
        <el-descriptions-item label="原值">{{ detail.originalValue ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="净值">{{ detail.netValue ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="保修到期日">{{ detail.warrantyDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="预计寿命（月）">{{ detail.expectedLife ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="存放地点" :span="2">
          {{ detail.location || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{
          detail.remark || '-'
        }}</el-descriptions-item>
        <el-descriptions-item label="附件" :span="2"><el-link v-for="url in detail.fileUrls || []" :key="url" :href="url" target="_blank" class="mr-3">{{ url.split('/').pop() || url }}</el-link><span v-if="!(detail.fileUrls || []).length">-</span></el-descriptions-item>
      </el-descriptions>

      <!-- 分类自定义字段 -->
      <template v-if="extFieldEntries.length > 0">
        <el-divider content-position="left">
          <span class="text-sm text-gray-500">自定义字段</span>
        </el-divider>
        <el-descriptions :column="2" border>
          <el-descriptions-item v-for="item in extFieldEntries" :key="item.key" :label="item.label">
            {{ item.value }}
          </el-descriptions-item>
        </el-descriptions>
      </template>

      <!-- 变更时间线 -->
      <el-divider content-position="left">
        <span class="text-sm text-gray-500">变更记录</span>
      </el-divider>
      <el-empty v-if="changeLogs.length === 0" description="暂无变更记录" :image-size="60" />
      <el-timeline v-else>
        <el-timeline-item
          v-for="log in changeLogs"
          :key="log.id"
          :timestamp="formatDate(log.operateTime)"
          placement="top"
        >
          <div class="flex items-center gap-2">
            <el-tag size="small">{{ changeTypeName(log.changeType) }}</el-tag>
            <span>{{ log.content }}</span>
          </div>
          <div class="mt-1 text-xs text-gray-500">
            操作人：{{ log.operatorName || '系统' }}
            <span v-if="log.beforeStatus !== log.afterStatus && log.afterStatus != null">
              ｜状态：{{ statusName(log.beforeStatus) }} → {{ statusName(log.afterStatus) }}
            </span>
          </div>
        </el-timeline-item>
      </el-timeline>
    </div>

    <template #footer>
      <el-button @click="dialogVisible = false">关 闭</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { formatDate } from '@/utils/formatTime'
import { getDictLabel } from '@/utils/dict'
import * as AssetApi from '@/api/eam/asset'
import * as CategoryFieldApi from '@/api/eam/categoryField'

defineOptions({ name: 'EamAssetDetail' })

const dialogVisible = ref(false)
const loading = ref(false)
const detail = ref<AssetApi.AssetVO>({} as AssetApi.AssetVO)
const changeLogs = ref<AssetApi.AssetChangeLogVO[]>([])
const fieldDefs = ref<CategoryFieldApi.CategoryFieldVO[]>([])

const CHANGE_TYPE_NAMES: Record<number, string> = {
  0: '创建',
  1: '编辑',
  2: '领用',
  3: '退还',
  4: '借用',
  5: '归还',
  6: '调拨',
  7: '维修',
  8: '维修完成',
  9: '申请报废',
  10: '报废通过',
  11: '报废驳回',
  12: '盘点',
  13: '标记丢失',
  14: '冻结',
  15: '解冻'
}
const changeTypeName = (type: number) => CHANGE_TYPE_NAMES[type] ?? '变更'
const statusName = (status?: number) =>
  status == null ? '-' : getDictLabel('eam_asset_status', status)

/** 用字段定义把 extFields 的 key 翻译成中文名，未定义的 key 直接显示原始 key */
const extFieldEntries = computed(() => {
  const values = detail.value.extFields || {}
  const snapshots = detail.value.extFieldLabels || {}
  const labelMap = new Map(fieldDefs.value.map((f) => [f.fieldKey, f.fieldName]))
  return Object.entries(values).map(([key, value]) => ({
    key,
    label: labelMap.get(key) ?? key,
    value: snapshots[key] || (value === null || value === undefined || value === '' ? '-' : String(value))
  }))
})

const open = async (id: number) => {
  dialogVisible.value = true
  loading.value = true
  detail.value = {} as AssetApi.AssetVO
  changeLogs.value = []
  fieldDefs.value = []
  try {
    detail.value = await AssetApi.getAsset(id)
    const [logs, defs] = await Promise.all([
      AssetApi.getChangeLogList(id),
      detail.value.categoryId
        ? CategoryFieldApi.getEffectiveFieldList(detail.value.categoryId)
        : Promise.resolve([])
    ])
    changeLogs.value = logs
    fieldDefs.value = defs.filter((field) => field.adminVisible !== false)
  } finally {
    loading.value = false
  }
}
defineExpose({ open })
</script>
