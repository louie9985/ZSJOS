<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, showSuccessToast } from 'vant'
import { getAreaTree, getLeadCatalog, createLead, type LeadCatalog, type LeadCreateResult } from '@/api/lead'
import { useDict } from '@/composables/useDict'
import type { DictItem } from '@/stores/app'
import type { AreaNode } from '@/components/AreaPicker.vue'
import type { SelectedProduct } from '@/components/ProductPicker.vue'
import AreaPicker from '@/components/AreaPicker.vue'
import ProductPicker from '@/components/ProductPicker.vue'
import ImageUploader from '@/components/ImageUploader.vue'
import { formatLeadNo } from '@/utils/format'
import { createIdempotencyKey } from '@/utils/idempotency'

defineOptions({ name: 'LeadSubmit' })

const router = useRouter()
const { loadSourceChannels, loadLeadCategories } = useDict()

// --- State ---
const currentStep = ref(0)
const submitting = ref(false)
const dataLoading = ref(true)
const dataError = ref('')

// 表单数据
const form = reactive({
  name: '',
  mobile: '',
  wechatId: '',
  area: undefined as { provinceCode: string; provinceName: string; cityCode: string; cityName: string } | undefined,
  products: [] as SelectedProduct[],
  sourceChannel: '',
  leadCategory: '',
  remark: ''
})

// 配置数据
const areaTree = ref<AreaNode[]>([])
const catalog = ref<LeadCatalog>({ categoryTree: [], spus: [], skus: [] })
const sourceChannels = ref<DictItem[]>([])
const leadCategories = ref<DictItem[]>([])

// 图片上传
const uploaderRef = ref<InstanceType<typeof ImageUploader>>()

// 提交结果
const submitResult = ref<LeadCreateResult>()

// --- Init ---
async function loadConfiguration() {
  dataLoading.value = true
  dataError.value = ''
  try {
    const [catalogData, sources, categories, areaData] = await Promise.all([
      getLeadCatalog(),
      loadSourceChannels(),
      loadLeadCategories(),
      getAreaTree()
    ])
    catalog.value = catalogData
    sourceChannels.value = sources
    leadCategories.value = categories
    areaTree.value = areaData
  } catch {
    dataError.value = '配置加载失败，请重试'
  } finally {
    dataLoading.value = false
  }
}

onMounted(loadConfiguration)

// --- Steps ---
const steps = [
  { title: '客户信息' },
  { title: '意向课程' },
  { title: '补充信息' },
  { title: '确认提交' }
]

// --- Validation ---
function validateStep1(): boolean {
  if (!form.name.trim()) { showToast('请输入客户姓名'); return false }
  if (!form.mobile.trim() && !form.wechatId.trim()) { showToast('手机号和微信号至少填一个'); return false }
  if (form.mobile.trim() && !/^1\d{10}$/.test(form.mobile.trim())) { showToast('手机号格式不正确'); return false }
  if (!form.area) { showToast('请选择客户地区'); return false }
  return true
}

function validateStep2(): boolean {
  if (form.products.length === 0) { showToast('请至少选择一个意向课程'); return false }
  if (!form.products.some(p => p.primary)) { showToast('请设置一个主意向课程'); return false }
  return true
}

function validateStep3(): boolean {
  if (!form.sourceChannel) { showToast('请选择来源渠道'); return false }
  if (!form.leadCategory) { showToast('请选择客资分类'); return false }
  if (uploaderRef.value?.isUploading()) { showToast('图片还在上传中，请稍候'); return false }
  if (uploaderRef.value?.hasError()) { showToast('有图片上传失败，请删除或重试'); return false }
  return true
}

function nextStep() {
  if (currentStep.value === 0 && !validateStep1()) return
  if (currentStep.value === 1 && !validateStep2()) return
  if (currentStep.value === 2 && !validateStep3()) return
  currentStep.value++
}

function prevStep() {
  if (currentStep.value > 0) currentStep.value--
}

// --- Submit ---
async function handleSubmit() {
  if (submitting.value) return
  submitting.value = true

  try {
    const attachmentIds = uploaderRef.value?.getUploadedIds() || []
    const result = await createLead({
      name: form.name.trim(),
      mobile: form.mobile.trim() || undefined,
      wechatId: form.wechatId.trim() || undefined,
      provinceCode: form.area!.provinceCode,
      cityCode: form.area!.cityCode,
      intendedProducts: form.products.map(p => ({
        spuRef: p.spuUnknown ? undefined : p.spuRef,
        skuRef: p.skuRef,
        spuUnknown: p.spuUnknown,
        skuUnknown: p.skuUnknown,
        primary: p.primary
      })),
      sourceChannel: form.sourceChannel,
      leadCategory: form.leadCategory,
      remark: form.remark.trim() || undefined,
      attachments: attachmentIds.map(id => ({ infraFileId: id })),
      dispatchMode: 'auto',
      idempotencyKey: createIdempotencyKey()
    })
    submitResult.value = result
    currentStep.value = 4 // 结果页
  } catch (cause) {
    if (cause instanceof TypeError) {
      showToast({ message: cause.message || '提交失败，请重试', type: 'fail' })
    }
  } finally {
    submitting.value = false
  }
}

// --- Result ---
function submitAnother() {
  form.name = ''
  form.mobile = ''
  form.wechatId = ''
  form.area = undefined
  form.products = []
  form.sourceChannel = ''
  form.leadCategory = ''
  form.remark = ''
  uploaderRef.value?.reset()
  submitResult.value = undefined
  currentStep.value = 0
}

function goDetail() {
  if (submitResult.value?.leadId) {
    router.push(`/lead/${submitResult.value.leadId}`)
  }
}

const outcomeInfo = computed(() => {
  if (!submitResult.value) return null
  const map: Record<string, { icon: string; color: string; title: string; desc: string }> = {
    activated: { icon: 'checked', color: 'var(--h5-success)', title: '提交成功', desc: formatLeadNo(submitResult.value.leadNo) },
    review_pending: { icon: 'info-o', color: 'var(--h5-warning)', title: '疑似重复，已进入复核', desc: `复核单号：#${submitResult.value.reviewId}` },
    duplicate_rejected: { icon: 'close', color: 'var(--h5-danger)', title: '提交被拒绝', desc: '已有相同活动客资，本次提交未创建' },
    duplicate_auto_closed: { icon: 'info-o', color: 'var(--h5-info)', title: '历史重复', desc: formatLeadNo(submitResult.value.leadNo) }
  }
  return map[submitResult.value.outcome] || map.activated
})

// 来源渠道和分类 picker
const showSourcePicker = ref(false)
const showCategoryPicker = ref(false)

const sourceLabel = computed(() => sourceChannels.value.find(s => s.value === form.sourceChannel)?.label || '')
const categoryLabel = computed(() => leadCategories.value.find(c => c.value === form.leadCategory)?.label || '')
</script>

<template>
  <div class="page-container submit-page">
    <van-nav-bar title="提交客资" left-arrow @click-left="$router.back()" />

    <!-- 步骤条 -->
    <van-steps v-if="currentStep < 4" :active="currentStep" class="submit-steps">
      <van-step v-for="step in steps" :key="step.title">{{ step.title }}</van-step>
    </van-steps>

    <!-- Loading -->
    <div v-if="dataLoading" style="padding: 60px; text-align: center;">
      <van-loading size="36" color="var(--h5-primary)">加载配置中...</van-loading>
    </div>

    <van-empty v-else-if="dataError" :description="dataError" image="error">
      <van-button size="small" type="primary" @click="loadConfiguration">重新加载</van-button>
    </van-empty>

    <template v-else>
      <!-- Step 1: 客户信息 -->
      <div v-show="currentStep === 0" class="submit-form">
        <div class="card">
          <van-field
            v-model="form.name"
            label="客户姓名"
            placeholder="请输入姓名"
            required
            maxlength="100"
            clearable
          />
          <van-field
            v-model="form.mobile"
            label="手机号"
            type="tel"
            placeholder="请输入手机号"
            maxlength="11"
            clearable
          >
            <template #extra>
              <span class="field-hint">手机号/微信至少填一个</span>
            </template>
          </van-field>
          <van-field
            v-model="form.wechatId"
            label="微信号"
            placeholder="请输入微信号"
            maxlength="64"
            clearable
          />
          <div class="field-label">客户地区 <span class="required">*</span></div>
          <AreaPicker v-model="form.area" :area-tree="areaTree" />
        </div>
      </div>

      <!-- Step 2: 意向课程 -->
      <div v-show="currentStep === 1" class="submit-form">
        <div class="card">
          <div class="field-label">意向课程 <span class="required">*</span></div>
          <p class="field-desc">至少选择一个课程，标记一个为主意向</p>
          <ProductPicker
            v-model="form.products"
            :spus="catalog.spus"
            :skus="catalog.skus"
            :category-tree="catalog.categoryTree"
          />
        </div>
      </div>

      <!-- Step 3: 补充信息 -->
      <div v-show="currentStep === 2" class="submit-form">
        <div class="card">
          <van-field
            :model-value="sourceLabel"
            label="来源渠道"
            placeholder="请选择"
            required
            readonly
            clickable
            right-icon="arrow-down"
            @click="showSourcePicker = true"
          />
          <van-field
            :model-value="categoryLabel"
            label="客资分类"
            placeholder="请选择"
            required
            readonly
            clickable
            right-icon="arrow-down"
            @click="showCategoryPicker = true"
          />
          <van-field
            v-model="form.remark"
            label="备注"
            type="textarea"
            placeholder="可选，描述客户需求"
            maxlength="1000"
            show-word-limit
            rows="3"
            autosize
          />
          <div class="field-label">图片附件</div>
          <p class="field-desc">可选，最多 9 张，JPG/PNG/WebP，单张不超过 10MB</p>
          <ImageUploader ref="uploaderRef" :max-count="9" />
        </div>

        <!-- 来源渠道 Picker -->
        <van-popup v-model:show="showSourcePicker" position="bottom" round>
          <van-picker
            :columns="sourceChannels.map(s => ({ text: s.label, value: s.value }))"
            @confirm="({ selectedValues }) => { form.sourceChannel = selectedValues[0] as string; showSourcePicker = false }"
            @cancel="showSourcePicker = false"
          />
        </van-popup>

        <!-- 客资分类 Picker -->
        <van-popup v-model:show="showCategoryPicker" position="bottom" round>
          <van-picker
            :columns="leadCategories.map(c => ({ text: c.label, value: c.value }))"
            @confirm="({ selectedValues }) => { form.leadCategory = selectedValues[0] as string; showCategoryPicker = false }"
            @cancel="showCategoryPicker = false"
          />
        </van-popup>
      </div>

      <!-- Step 4: 确认提交 -->
      <div v-show="currentStep === 3" class="submit-form">
        <div class="card">
          <div class="confirm-section">
            <div class="confirm-title">客户信息</div>
            <div class="confirm-row">
              <span class="confirm-label">姓名</span>
              <span class="confirm-value">{{ form.name }}</span>
            </div>
            <div v-if="form.mobile" class="confirm-row">
              <span class="confirm-label">手机号</span>
              <span class="confirm-value">{{ form.mobile }}</span>
            </div>
            <div v-if="form.wechatId" class="confirm-row">
              <span class="confirm-label">微信号</span>
              <span class="confirm-value">{{ form.wechatId }}</span>
            </div>
            <div class="confirm-row">
              <span class="confirm-label">地区</span>
              <span class="confirm-value">{{ form.area?.provinceName }} / {{ form.area?.cityName }}</span>
            </div>
          </div>

          <div class="confirm-section">
            <div class="confirm-title">意向课程</div>
            <div v-for="p in form.products" :key="p.spuRef" class="confirm-row">
              <span class="confirm-value">
                <van-tag v-if="p.primary" type="primary" size="medium" style="margin-right: 4px;">主</van-tag>
                {{ p.spuName }}
              </span>
            </div>
          </div>

          <div class="confirm-section">
            <div class="confirm-title">补充信息</div>
            <div class="confirm-row">
              <span class="confirm-label">来源渠道</span>
              <span class="confirm-value">{{ sourceLabel }}</span>
            </div>
            <div class="confirm-row">
              <span class="confirm-label">客资分类</span>
              <span class="confirm-value">{{ categoryLabel }}</span>
            </div>
            <div v-if="form.remark" class="confirm-row">
              <span class="confirm-label">备注</span>
              <span class="confirm-value">{{ form.remark }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 结果页 -->
      <div v-if="currentStep === 4" class="submit-result">
        <div class="card" style="text-align: center; padding: 40px 20px;">
          <van-icon :name="outcomeInfo?.icon || 'checked'" :color="outcomeInfo?.color" size="60" />
          <div class="submit-result__title">{{ outcomeInfo?.title }}</div>
          <div class="submit-result__desc">{{ outcomeInfo?.desc }}</div>
          <div class="submit-result__actions">
            <van-button type="primary" round @click="submitAnother">继续提交</van-button>
            <van-button v-if="submitResult?.leadId" round plain @click="goDetail">查看详情</van-button>
          </div>
        </div>
      </div>

      <!-- 底部按钮 -->
      <div v-if="currentStep < 4" class="submit-actions safe-area-bottom">
        <van-button v-if="currentStep > 0" round plain @click="prevStep">上一步</van-button>
        <van-button v-if="currentStep < 3" type="primary" round @click="nextStep">下一步</van-button>
        <van-button v-if="currentStep === 3" type="primary" round :loading="submitting" @click="handleSubmit">确认提交</van-button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.submit-steps {
  padding: 16px 16px 0;
}

.submit-form {
  padding-bottom: 80px;
}

.field-label {
  font-size: 14px;
  color: var(--h5-text-primary);
  padding: 12px 16px 6px;
  font-weight: 500;
}
.field-desc {
  font-size: 12px;
  color: var(--h5-text-secondary);
  padding: 0 16px 10px;
}
.field-hint {
  font-size: 11px;
  color: var(--h5-text-placeholder);
}
.required {
  color: var(--h5-danger);
}

.submit-actions {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  display: flex;
  gap: 12px;
  padding: 12px 16px;
  background: var(--h5-card-bg);
  box-shadow: 0 -2px 8px rgba(0, 0, 0, 0.04);
  z-index: 10;
}
.submit-actions .van-button {
  flex: 1;
  height: 44px;
}

/* 确认页 */
.confirm-section {
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--h5-divider);
}
.confirm-section:last-child {
  border-bottom: none;
  margin-bottom: 0;
}
.confirm-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--h5-text-primary);
  margin-bottom: 8px;
}
.confirm-row {
  display: flex;
  justify-content: space-between;
  padding: 4px 0;
  font-size: 13px;
}
.confirm-label {
  color: var(--h5-text-secondary);
}
.confirm-value {
  color: var(--h5-text-primary);
  text-align: right;
  max-width: 60%;
  word-break: break-all;
}

/* 结果页 */
.submit-result__title {
  font-size: 18px;
  font-weight: 600;
  margin-top: 16px;
  color: var(--h5-text-primary);
}
.submit-result__desc {
  font-size: 14px;
  color: var(--h5-text-secondary);
  margin-top: 8px;
}
.submit-result__actions {
  display: flex;
  gap: 12px;
  justify-content: center;
  margin-top: 24px;
}
</style>
