<template>
  <Dialog v-model="dialogVisible" :title="`盘点明细 - ${inventory.name ?? ''}`" width="1000px">
    <div class="mb-3 flex items-center gap-4">
      <el-tag>{{ inventory.no }}</el-tag>
      <span class="text-sm text-gray-600">
        应盘 {{ inventory.totalCount }} ｜ 已盘 {{ inventory.checkedCount }} ｜ 正常
        {{ inventory.normalCount }} ｜ 异常 {{ inventory.abnormalCount }}
      </span>
      <el-tag :type="inventory.status === 1 ? 'success' : 'warning'" size="small">
        {{ inventory.status === 1 ? '已完成' : '进行中' }}
      </el-tag>
    </div>

    <el-table v-loading="loading" :data="details" max-height="480">
      <el-table-column label="资产编号" prop="assetCode" min-width="140" fixed="left" />
      <el-table-column label="资产名称" prop="assetName" min-width="150" show-overflow-tooltip />
      <el-table-column label="账面使用人" prop="expectUserName" min-width="110" />
      <el-table-column
        label="账面地点"
        prop="expectLocation"
        min-width="140"
        show-overflow-tooltip
      />
      <el-table-column label="盘点结果" min-width="110" align="center">
        <template #default="{ row }">
          <dict-tag :type="'eam_inventory_result'" :value="row.result" />
        </template>
      </el-table-column>
      <el-table-column label="实盘地点" prop="actualLocation" min-width="140" show-overflow-tooltip />
      <el-table-column label="操作" width="230" align="center" fixed="right">
        <template #default="{ row }">
          <template v-if="inventory.status === 0">
            <el-button
              v-hasPermi="['eam:inventory:update']"
              link
              type="primary"
              @click="openCheck(row)"
            >
              录入
            </el-button>
            <!-- 位置不符：把实盘归属写回台账 -->
            <el-button
              v-if="row.result === InventoryResult.LOCATION_MISMATCH"
              v-hasPermi="['eam:inventory:update']"
              link
              type="warning"
              @click="handleSync(row.id)"
            >
              同步归属
            </el-button>
            <!-- 未找到：把资产标记为已丢失 -->
            <el-button
              v-if="row.result === InventoryResult.NOT_FOUND"
              v-hasPermi="['eam:inventory:update']"
              link
              type="danger"
              @click="handleMarkLost(row.id)"
            >
              标记丢失
            </el-button>
          </template>
          <span v-else class="text-gray-400">已完成</span>
        </template>
      </el-table-column>
    </el-table>

    <template #footer>
      <el-button @click="dialogVisible = false">关 闭</el-button>
    </template>
  </Dialog>

  <!-- 录入盘点结果 -->
  <Dialog v-model="checkVisible" title="录入盘点结果" width="520px">
    <el-form ref="checkFormRef" :model="checkForm" :rules="checkRules" label-width="100px">
      <el-form-item label="资产">
        <span>{{ currentDetail.assetCode }} {{ currentDetail.assetName }}</span>
      </el-form-item>
      <el-form-item label="盘点结果" prop="result">
        <el-radio-group v-model="checkForm.result">
          <el-radio :value="InventoryResult.NORMAL">正常</el-radio>
          <el-radio :value="InventoryResult.LOCATION_MISMATCH">位置不符</el-radio>
          <el-radio :value="InventoryResult.NOT_FOUND">未找到</el-radio>
        </el-radio-group>
      </el-form-item>
      <template v-if="checkForm.result === InventoryResult.LOCATION_MISMATCH">
        <el-form-item label="实盘使用人" prop="actualUserId">
          <el-select
            v-model="checkForm.actualUserId"
            filterable
            clearable
            class="!w-full"
            placeholder="请选择实际使用人"
          >
            <el-option
              v-for="user in userList"
              :key="user.id"
              :label="user.nickname"
              :value="user.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="实盘部门" prop="actualDeptId">
          <el-tree-select
            v-model="checkForm.actualDeptId"
            :data="deptTree"
            :props="{ label: 'name', children: 'children', value: 'id' }"
            check-strictly
            node-key="id"
            clearable
            class="!w-full"
            placeholder="请选择实际部门"
          />
        </el-form-item>
        <el-form-item label="实盘地点" prop="actualLocation">
          <el-input v-model="checkForm.actualLocation" placeholder="请输入实际存放地点" />
        </el-form-item>
      </template>
      <el-form-item label="备注" prop="remark">
        <el-input v-model="checkForm.remark" type="textarea" :rows="2" placeholder="请输入备注" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :disabled="checkLoading" type="primary" @click="submitCheck">确 定</el-button>
      <el-button @click="checkVisible = false">取 消</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { handleTree } from '@/utils/tree'
import * as InventoryApi from '@/api/eam/inventory'
import { InventoryResult } from '@/api/eam/inventory'
import * as DeptApi from '@/api/system/dept'
import * as UserApi from '@/api/system/user'

defineOptions({ name: 'EamInventoryDetail' })

const emit = defineEmits(['success'])

const message = useMessage()

const dialogVisible = ref(false)
const loading = ref(false)
const inventory = ref<InventoryApi.InventoryVO>({} as InventoryApi.InventoryVO)
const details = ref<InventoryApi.InventoryDetailVO[]>([])

const deptTree = ref<any[]>([])
const userList = ref<any[]>([])

const checkVisible = ref(false)
const checkLoading = ref(false)
const checkFormRef = ref()
const currentDetail = ref<InventoryApi.InventoryDetailVO>({} as InventoryApi.InventoryDetailVO)
const checkForm = ref<InventoryApi.InventoryCheckVO>({
  detailId: 0,
  result: InventoryResult.NORMAL
})
const checkRules = reactive({
  result: [{ required: true, message: '盘点结果不能为空', trigger: 'change' }]
})

const loadDetails = async () => {
  loading.value = true
  try {
    details.value = await InventoryApi.getDetailList(inventory.value.id!)
    // 刷新头部统计，让已盘/异常计数与明细保持一致
    inventory.value = await InventoryApi.getInventory(inventory.value.id!)
  } finally {
    loading.value = false
  }
}

const open = async (row: InventoryApi.InventoryVO) => {
  inventory.value = row
  dialogVisible.value = true
  await loadDetails()
}
defineExpose({ open })

const openCheck = (row: InventoryApi.InventoryDetailVO) => {
  currentDetail.value = row
  checkForm.value = {
    detailId: row.id,
    result: row.result === InventoryResult.UNCHECKED ? InventoryResult.NORMAL : row.result,
    actualUserId: row.actualUserId ?? row.expectUserId,
    actualDeptId: row.actualDeptId ?? row.expectDeptId,
    actualLocation: row.actualLocation ?? row.expectLocation,
    remark: row.remark
  }
  checkVisible.value = true
}

const submitCheck = async () => {
  await checkFormRef.value.validate()
  checkLoading.value = true
  try {
    await InventoryApi.checkDetail(checkForm.value)
    message.success('已录入')
    checkVisible.value = false
    await loadDetails()
    emit('success')
  } finally {
    checkLoading.value = false
  }
}

const handleSync = async (detailId: number) => {
  try {
    await message.confirm('确认把实盘归属同步回资产台账？')
    await InventoryApi.syncDetailToAsset(detailId)
    message.success('已同步')
    await loadDetails()
  } catch {}
}

const handleMarkLost = async (detailId: number) => {
  try {
    await message.confirm('确认把该资产标记为已丢失？此操作会改变资产状态')
    await InventoryApi.markLost(detailId)
    message.success('已标记丢失')
    await loadDetails()
  } catch {}
}

onMounted(async () => {
  deptTree.value = handleTree(await DeptApi.getSimpleDeptList())
  userList.value = await UserApi.getSimpleUserList()
})
</script>
