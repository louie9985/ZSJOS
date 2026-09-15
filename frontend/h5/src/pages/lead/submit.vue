<script setup lang="ts">
import ProductSpecs from '../../components/ProductSpecs.vue'
import { ref, reactive, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, showSuccessToast } from 'vant'
import { getAreaTree, getLeadCatalog, createLead, type LeadCatalog, type LeadCreateResult, type UploadResult } from '@/api/lead'
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
const confirmedAttachments = ref<UploadResult[]>([])
const submissionIdempotencyKey = ref<string>()

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
const currentStepBadge = computed(() => currentStep.value < steps.length ? `第 ${currentStep.value + 1} / ${steps.length} 步` : '已完成')

// --- Validation ---
function validateStep1(): boolean {
  if (!form.name.trim()) { showToast('请输入客户姓名'); return false }
  if (!form.mobile.trim() && !form.wechatId.trim()) { showToast('手机号和微信号至少填一个'); return false }
  if (form.mobile.trim() && !/^1\d{10}$/.test(form.mobile.trim())) { showToast('手机号格式不正确'); return false }
  if (/\s/.test(form.mobile) || /\s/.test(form.wechatId)) { showToast('手机号和微信号不能包含空格'); return false }
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
  if (currentStep.value === 2) {
    if (!validateStep3()) return
    confirmedAttachments.value = uploaderRef.value?.getUploadedFiles() || []
  }
  currentStep.value++
}

function prevStep() {
  if (currentStep.value === 3) submissionIdempotencyKey.value = undefined
  if (currentStep.value > 0) currentStep.value--
}

// --- Submit ---
async function handleSubmit() {
  if (submitting.value) return
  if (!validateStep3()) {
    currentStep.value = 2
    return
  }
  const attachments = uploaderRef.value?.getUploadedFiles() || []
  confirmedAttachments.value = attachments
  submitting.value = true

  try {
    const idempotencyKey = submissionIdempotencyKey.value || createIdempotencyKey()
    submissionIdempotencyKey.value = idempotencyKey
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
      attachments: attachments.map(file => ({ infraFileId: file.infraFileId })),
      dispatchMode: 'auto',
      idempotencyKey
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
  confirmedAttachments.value = []
  submissionIdempotencyKey.value = undefined
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
  const result = submitResult.value
  const map: Record<string, { icon: string; color: string; title: string; desc: string }> = {
    activated: { icon: 'checked', color: 'var(--h5-success)', title: '提交成功', desc: formatLeadNo(submitResult.value.leadNo) },
    created: { icon: 'checked', color: 'var(--h5-success)', title: '提交成功', desc: formatLeadNo(submitResult.value.leadNo) },
    review_pending: { icon: 'info-o', color: 'var(--h5-warning)', title: '疑似重复，等待管理员审核', desc: `复核单号：#${submitResult.value.reviewId}，请勿重复提交` },
    duplicate_rejected: { icon: 'close', color: 'var(--h5-danger)', title: '联系方式已存在', desc: '本次提交未创建客资，请联系管理员' },
    duplicate_auto_closed: { icon: 'info-o', color: 'var(--h5-info)', title: '疑似重复，已自动关闭', desc: '本次提交未创建客资' }
  }
  return map[submitResult.value.outcome] || map.activated
})

// 来源渠道和分类 picker
const showSourcePicker = ref(false)
const showCategoryPicker = ref(false)

const sourceLabel = computed(() => sourceChannels.value.find(s => s.value === form.sourceChannel)?.label || '')
const categoryLabel = computed(() => leadCategories.value.find(c => c.value === form.leadCategory)?.label || '')

function selectSource(value: string) {
  form.sourceChannel = value
  showSourcePicker.value = false
}

function selectCategory(value: string) {
  form.leadCategory = value
  showCategoryPicker.value = false
}
</script>

<template>
  <div class="page-container submit-page">
    <section class="card page-hero submit-hero hero-surface">
      <div class="page-hero__head">
        <div>
          <div class="page-hero__title">提交客资</div>
        </div>
        <div class="page-hero__aside">
          <span class="page-chip">{{ currentStepBadge }}</span>
          <HelpPopover
            text="按步骤完善客户信息，提交后系统自动分配。"
            placement="bottom-end"
            aria-label="查看提交说明"
          />
        </div>
      </div>

      <van-steps v-if="currentStep < 4" :active="currentStep" class="submit-steps">
        <van-step v-for="step in steps" :key="step.title">{{ step.title }}</van-step>
      </van-steps>
    </section>

    <div v-if="dataLoading" class="card submit-state">
      <van-loading size="36" color="var(--h5-primary)">加载配置中...</van-loading>
    </div>

    <van-empty v-else-if="dataError" class="page-empty-card" :description="dataError" image="error">
      <van-button size="small" type="primary" @click="loadConfiguration">重新加载</van-button>
    </van-empty>

    <template v-else>
      <section v-show="currentStep === 0" class="card submit-section customer-info-section">
        <div class="submit-form">
          <van-field
            v-model="form.name"
            label="客户姓名"
            placeholder="请输入姓名"
            aria-required="true"
            maxlength="100"
            clearable
          >
            <template #label>
              <span class="h5-required-label">客户姓名</span>
            </template>
          </van-field>
          <van-field
            v-model="form.mobile"
            type="tel"
            placeholder="请输入手机号"
            maxlength="11"
            clearable
          >
            <template #label>
              <span class="contact-field-label">
                <span>手机号</span>
                <HelpPopover
                  text="手机号和微信号至少填一个"
                  placement="top-start"
                  aria-label="查看手机号填写说明"
                  nowrap
                />
              </span>
            </template>
          </van-field>
          <van-field
            v-model="form.wechatId"
            placeholder="请输入微信号"
            maxlength="64"
            clearable
          >
            <template #label>
              <span class="contact-field-label">
                <span>微信号</span>
                <HelpPopover
                  text="手机号和微信号至少填一个"
                  placement="top-start"
                  aria-label="查看微信号填写说明"
                  nowrap
                />
              </span>
            </template>
          </van-field>
          <div class="customer-area-field">
            <div class="customer-area-field__label"><span class="h5-required-label">客户地区</span></div>
            <AreaPicker v-model="form.area" :area-tree="areaTree" class="customer-area-field__picker" />
          </div>
        </div>
      </section>

      <section v-show="currentStep === 1" class="card submit-section">
        <div class="submit-form">
          <div class="field-label"><span class="h5-required-label">意向课程</span></div>
          <p class="field-desc">至少选择一个课程，标记一个为主意向</p>
          <ProductPicker
            v-model="form.products"
            :spus="catalog.spus"
            :skus="catalog.skus"
            :category-tree="catalog.categoryTree"
          />
        </div>
      </section>

      <section v-show="currentStep === 2" class="card submit-section">
        <div class="submit-form">
          <van-field
            :model-value="sourceLabel"
            label="来源渠道"
            placeholder="请选择"
            required
            readonly
            clickable
            right-icon="arrow"
            @click="showSourcePicker = true"
          />
          <van-field
            :model-value="categoryLabel"
            label="客资分类"
            placeholder="请选择"
            required
            readonly
            clickable
            right-icon="arrow"
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

        <van-popup v-model:show="showSourcePicker" position="bottom" round teleport="body" class="submit-picker norem" overlay-class="submit-picker-overlay" safe-area-inset-bottom>
          <div class="submit-choice-sheet">
            <div class="submit-choice-sheet__header">
              <strong>选择来源渠道</strong>
              <button type="button" aria-label="关闭来源渠道选择" title="关闭" @click="showSourcePicker = false">
                <van-icon name="cross" size="20" />
              </button>
            </div>
            <div v-if="sourceChannels.length" class="submit-choice-sheet__list" role="radiogroup" aria-label="来源渠道">
              <button
                v-for="item in sourceChannels"
                :key="item.value"
                type="button"
                role="radio"
                :aria-checked="form.sourceChannel === item.value"
                :class="{ 'is-active': form.sourceChannel === item.value }"
                @click="selectSource(item.value)"
              >
                <span>{{ item.label }}</span>
                <van-icon v-if="form.sourceChannel === item.value" name="success" size="18" />
              </button>
            </div>
            <van-empty v-else image="search" description="来源渠道暂未配置" />
          </div>
        </van-popup>

        <van-popup v-model:show="showCategoryPicker" position="bottom" round teleport="body" class="submit-picker norem" overlay-class="submit-picker-overlay" safe-area-inset-bottom>
          <div class="submit-choice-sheet">
            <div class="submit-choice-sheet__header">
              <strong>选择客资分类</strong>
              <button type="button" aria-label="关闭客资分类选择" title="关闭" @click="showCategoryPicker = false">
                <van-icon name="cross" size="20" />
              </button>
            </div>
            <div v-if="leadCategories.length" class="submit-choice-sheet__list" role="radiogroup" aria-label="客资分类">
              <button
                v-for="item in leadCategories"
                :key="item.value"
                type="button"
                role="radio"
                :aria-checked="form.leadCategory === item.value"
                :class="{ 'is-active': form.leadCategory === item.value }"
                @click="selectCategory(item.value)"
              >
                <span>{{ item.label }}</span>
                <van-icon v-if="form.leadCategory === item.value" name="success" size="18" />
              </button>
            </div>
            <van-empty v-else image="search" description="客资分类暂未配置" />
          </div>
        </van-popup>
      </section>

      <section v-show="currentStep === 3" class="card submit-section">
        <div class="submit-form">
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
                {{ p.spuName }}<ProductSpecs :product="p" />
                <span v-if="p.skuName">{{ p.skuName }}</span>
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
            <div class="confirm-row">
              <span class="confirm-label">派单方式</span>
              <span class="confirm-value">系统自动分配</span>
            </div>
            <div class="field-hint">提交后由系统自动分配销售，无需手动选择</div>
            <div v-if="form.remark" class="confirm-row">
              <span class="confirm-label">备注</span>
              <span class="confirm-value">{{ form.remark }}</span>
            </div>
            <div class="confirm-row">
              <span class="confirm-label">图片附件</span>
              <span class="confirm-value">{{ confirmedAttachments.length }} 张</span>
            </div>
            <div v-if="confirmedAttachments.length" class="confirm-attachments">
              <div v-for="file in confirmedAttachments" :key="file.infraFileId" class="confirm-attachment">
                <img :src="file.fileUrl" :alt="file.originalName" />
                <span>{{ file.originalName }}</span>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section v-if="currentStep === 4" class="card submit-result">
        <div class="submit-result__icon">
          <van-icon :name="outcomeInfo?.icon || 'checked'" :color="outcomeInfo?.color" size="60" />
        </div>
        <div class="submit-result__title">{{ outcomeInfo?.title }}</div>
        <div class="submit-result__desc">{{ outcomeInfo?.desc }}</div>
        <div v-if="submitResult?.existingQualificationStatus || submitResult?.existingOperationalStatus" class="submit-result__detail">
          有效性：{{ submitResult.existingQualificationStatus || '--' }} · 运行状态：{{ submitResult.existingOperationalStatus || '--' }}
        </div>
        <div class="submit-result__actions">
          <van-button type="primary" round @click="submitAnother">继续提交</van-button>
          <van-button v-if="submitResult?.leadId" round plain @click="goDetail">查看详情</van-button>
        </div>
      </section>

      <div v-if="currentStep < 4" class="submit-actions safe-area-bottom">
        <van-button v-if="currentStep > 0" round plain @click="prevStep">上一步</van-button>
        <van-button v-if="currentStep < 3" type="primary" round @click="nextStep">下一步</van-button>
        <van-button v-if="currentStep === 3" type="primary" round :loading="submitting" @click="handleSubmit">确认提交</van-button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.submit-page {
  padding-bottom: 152px;
}

.submit-hero {
  margin-top: 20PX;
  padding: 14px 16px 12px;
}

.submit-hero .page-hero__aside {
  display: flex;
  align-items: center;
  gap: 8px;
}

.submit-steps {
  --van-steps-background: transparent;
  --van-step-line-color: var(--h5-glass-divider);

  margin-top: 0;
  padding: 10px 2px 0;
  border-top: 1px solid var(--h5-glass-divider);
}

.submit-steps :deep(.van-step__title) {
  margin-top: 2px;
  color: var(--h5-text-secondary);
  font-size: 12px;
  white-space: nowrap;
}

.submit-steps :deep(.van-step__circle-container) {
  margin-bottom: 4px;
  background: transparent;
}

.submit-steps :deep(.van-step--process .van-step__title) {
  color: var(--h5-primary);
  font-weight: 600;
}

.submit-state {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 120px;
}

.submit-section {
  gap: 0;
  margin-top: 12px;
  padding: 16px 16px 18px;
}

.customer-info-section {
  background: var(--h5-content-surface);
}

.submit-form {
  display: flex;
  flex-direction: column;
  gap: 0;
  padding-top: 6px;
}

.submit-form :deep(.van-cell) {
  padding: 13px 0;
  background: transparent;
}

.submit-form :deep(.van-field__label) {
  width: 82px;
  color: var(--h5-text-primary);
  font-size: 13px;
}

.submit-form :deep(.van-field__control) {
  color: var(--h5-text-primary);
  font-size: 14px;
}

.contact-field-label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.contact-field-label :deep(.help-popover__button) {
  margin: -8px -4px -8px -6px;
}

.field-label {
  padding: 14px 0 6px;
  font-size: 14px;
  font-weight: 500;
  color: var(--h5-text-primary);
}

.field-desc {
  padding: 0 0 10px;
  font-size: 12px;
  color: var(--h5-text-secondary);
}

.field-hint {
  padding: 2px 0 8px;
  font-size: 11px;
  color: var(--h5-text-placeholder);
}

.customer-info-section :deep(.van-field__label),
.customer-info-section :deep(.van-field__control),
.customer-info-section .field-label {
  font-size: 14px;
  font-weight: 400;
}

.customer-area-field {
  display: flex;
  align-items: center;
  min-height: 52px;
}

.customer-area-field__label {
  flex: 0 0 82px;
  margin-right: var(--van-field-label-margin-right);
  color: var(--h5-text-primary);
  font-size: 14px;
}

.customer-area-field__picker {
  flex: 1;
  min-width: 0;
}

.customer-area-field :deep(.van-cell) {
  min-width: 0;
  padding: 13px 0;
  border-bottom: 0;
}

.customer-area-field :deep(.van-field__control) {
  overflow: hidden;
  font-size: 14px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.submit-picker {
  right: 0;
  left: 0;
  width: min(100%, 10rem);
  max-height: min(64dvh, 560px);
  margin: 0 auto;
  overflow: hidden;
  border: 1px solid var(--h5-glass-border);
  border-bottom: 0;
  border-radius: 24px 24px 0 0;
  background: color-mix(in srgb, var(--h5-card-bg) 74%, transparent);
  box-shadow: var(--h5-glass-shadow-floating);
  backdrop-filter: saturate(170%) blur(var(--h5-glass-blur-strong));
  -webkit-backdrop-filter: saturate(170%) blur(var(--h5-glass-blur-strong));
}

.submit-choice-sheet {
  display: flex;
  max-height: min(64dvh, 560px);
  flex-direction: column;
  background: transparent;
}

.submit-choice-sheet::before {
  width: 32px;
  height: 3px;
  flex: 0 0 auto;
  align-self: center;
  margin-top: 8px;
  border-radius: 999px;
  background: var(--h5-glass-divider);
  content: '';
}

.submit-choice-sheet__header {
  display: flex;
  flex: 0 0 auto;
  min-height: 58px;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px 10px 20px;
  border-bottom: 1px solid var(--h5-glass-divider);
  background: transparent;
}

.submit-choice-sheet__header strong {
  color: var(--h5-text-primary);
  font-size: 17px;
  font-weight: 600;
}

.submit-choice-sheet__header button {
  display: flex;
  width: 36px;
  height: 36px;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: var(--h5-glass-sunken);
  color: var(--h5-text-secondary);
}

.submit-choice-sheet__list {
  min-height: 0;
  padding: 6px 16px 14px;
  overflow-y: auto;
  overscroll-behavior: contain;
  background: transparent;
}

.submit-choice-sheet__list button {
  display: flex;
  width: 100%;
  min-height: 48px;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 4px;
  border: 0;
  border-bottom: 1px solid var(--h5-glass-divider);
  background: transparent;
  color: var(--h5-text-primary);
  font: inherit;
  font-size: 14px;
  text-align: left;
}

.submit-choice-sheet__list button.is-active {
  border-radius: 10px;
  background: var(--h5-primary-opacity);
  color: var(--h5-primary);
  font-weight: 600;
}

.submit-actions {
  position: fixed;
  right: 0;
  bottom: 66px;
  left: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  min-height: 64px;
  padding: 10px 16px;
  border-top: 0;
  background: transparent;
  box-shadow: none;
  z-index: 10;
}

.submit-actions .van-button {
  width: auto;
  min-width: 0;
  flex: 1 1 0;
  height: 44px;
}

.submit-actions .van-button:only-child {
  flex: 0 1 320px;
  width: min(100%, 320px);
}

.confirm-section {
  margin-bottom: 14px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--h5-divider);
}

.confirm-section:last-child {
  margin-bottom: 0;
  padding-bottom: 0;
  border-bottom: none;
}

.confirm-title {
  margin-bottom: 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--h5-text-primary);
}

.confirm-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 6px 0;
  font-size: 13px;
  line-height: 1.45;
}

.confirm-label {
  flex: 0 0 52px;
  color: var(--h5-text-secondary);
}

.confirm-value {
  max-width: 60%;
  color: var(--h5-text-primary);
  text-align: right;
  word-break: break-all;
}

.confirm-attachments {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin-top: 8px;
}

.confirm-attachment {
  min-width: 0;
}

.confirm-attachment img {
  display: block;
  width: 100%;
  aspect-ratio: 1;
  object-fit: cover;
  border-radius: 10px;
}

.confirm-attachment span {
  display: block;
  overflow: hidden;
  margin-top: 4px;
  color: var(--h5-text-secondary);
  font-size: 11px;
  line-height: 16px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.submit-result {
  margin-top: 20px;
  padding: 32px 20px;
  text-align: center;
}

.submit-result__icon {
  display: flex;
  justify-content: center;
}

.submit-result__title {
  margin-top: 16px;
  font-size: 18px;
  font-weight: 700;
  color: var(--h5-text-primary);
}

.submit-result__desc {
  margin-top: 8px;
  font-size: 14px;
  color: var(--h5-text-secondary);
}

.submit-result__detail {
  margin-top: 12px;
  padding: 10px 12px;
  border-radius: 12px;
  background: var(--h5-primary-opacity);
  color: var(--h5-primary);
  font-size: 12px;
  line-height: 1.5;
}

.submit-result__actions {
  display: flex;
  gap: 10px;
  margin-top: 20px;
}

.submit-result__actions .van-button {
  flex: 1;
  min-width: 0;
}

@supports not ((backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px))) {
  .submit-actions { background: transparent; }
}

@media (prefers-reduced-transparency: reduce) {
  .submit-actions {
    background: transparent;
    backdrop-filter: none;
    -webkit-backdrop-filter: none;
  }
}
</style>
