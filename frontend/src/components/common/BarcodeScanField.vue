<template>
  <div class="barcode-scan-field">
    <el-input
      ref="inputRef"
      v-model="inputValue"
      :placeholder="placeholder"
      :disabled="disabled"
      clearable
      autocomplete="off"
      @keyup.enter="submitInput"
    >
      <template #prefix>
        <el-icon><Aim /></el-icon>
      </template>
      <template #append>
        <el-tooltip content="摄像头扫码" placement="top">
          <el-button
            class="barcode-scan-field__camera"
            :disabled="disabled"
            aria-label="打开摄像头扫码"
            @click="openCamera"
          >
            <el-icon><Camera /></el-icon>
          </el-button>
        </el-tooltip>
      </template>
    </el-input>

    <el-dialog
      v-model="cameraVisible"
      title="摄像头扫码"
      width="min(520px, calc(100vw - 32px))"
      :teleported="false"
      destroy-on-close
      @closed="stopCamera"
    >
      <div class="barcode-camera" aria-live="polite">
        <video
          v-show="cameraState === 'active'"
          ref="videoRef"
          class="barcode-camera__video"
          autoplay
          muted
          playsinline
        />

        <div v-if="cameraState === 'requesting'" class="barcode-camera__status">
          <el-icon class="is-loading"><Loading /></el-icon>
          <span>正在连接摄像头</span>
        </div>

        <div v-else-if="cameraState === 'unsupported'" class="barcode-camera__status barcode-camera__status--warning">
          <el-icon><WarningFilled /></el-icon>
          <span>当前浏览器不支持摄像头识码，请使用扫码枪或手工输入</span>
        </div>

        <div v-else-if="cameraState === 'error'" class="barcode-camera__status barcode-camera__status--warning">
          <el-icon><WarningFilled /></el-icon>
          <span>{{ cameraMessage }}</span>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref } from 'vue'
import type { InputInstance } from 'element-plus'
import { Aim, Camera, Loading, WarningFilled } from '@element-plus/icons-vue'

interface BarcodeDetection {
  rawValue: string
}

interface BarcodeDetectorInstance {
  detect(source: HTMLVideoElement): Promise<BarcodeDetection[]>
}

interface BarcodeDetectorConstructor {
  new(): BarcodeDetectorInstance
}

withDefaults(defineProps<{
  placeholder?: string
  disabled?: boolean
}>(), {
  placeholder: '扫描或输入商品条码',
  disabled: false
})

type CameraState = 'idle' | 'requesting' | 'active' | 'unsupported' | 'error'

const emit = defineEmits<{
  scan: [barcode: string]
  cameraError: [message: string]
  cameraState: [state: CameraState]
}>()

const inputRef = ref<InputInstance>()
const inputValue = ref('')
const cameraVisible = ref(false)
const cameraState = ref<CameraState>('idle')
const cameraMessage = ref('')
const videoRef = ref<HTMLVideoElement>()

let stream: MediaStream | null = null
let detector: BarcodeDetectorInstance | null = null
let animationFrameId: number | null = null

const setCameraState = (state: CameraState) => {
  if (cameraState.value === state) return
  cameraState.value = state
  emit('cameraState', state)
}

const submitInput = () => {
  const barcode = inputValue.value.trim()
  if (!barcode) return
  emit('scan', barcode)
  inputValue.value = ''
  void nextTick(() => inputRef.value?.focus())
}

const detectorConstructor = () => (
  globalThis as typeof globalThis & { BarcodeDetector?: BarcodeDetectorConstructor }
).BarcodeDetector

const openCamera = async () => {
  cameraVisible.value = true
  cameraMessage.value = ''
  await nextTick()

  const Detector = detectorConstructor()
  if (!Detector || !navigator.mediaDevices?.getUserMedia) {
    setCameraState('unsupported')
    return
  }

  setCameraState('requesting')
  try {
    stream = await navigator.mediaDevices.getUserMedia({
      audio: false,
      video: { facingMode: { ideal: 'environment' } }
    })
    if (!cameraVisible.value) {
      stopCamera()
      return
    }

    const video = videoRef.value
    if (!video) {
      throw new Error('摄像头预览不可用')
    }
    video.srcObject = stream
    void video.play().catch(() => undefined)
    detector = new Detector()
    setCameraState('active')
    void detectFrame()
  } catch {
    setCameraError('无法启用摄像头，请检查浏览器权限后重试')
  }
}

const detectFrame = async () => {
  if (cameraState.value !== 'active' || !detector || !videoRef.value) return

  try {
    const detections = await detector.detect(videoRef.value)
    const barcode = detections.find((item) => item.rawValue.trim())?.rawValue.trim()
    if (barcode) {
      emit('scan', barcode)
      cameraVisible.value = false
      stopCamera()
      return
    }
  } catch {
    // A video element can be temporarily unreadable while its first frame loads.
  }

  if (cameraState.value === 'active') {
    animationFrameId = requestAnimationFrame(() => void detectFrame())
  }
}

const setCameraError = (message: string) => {
  stopMediaTracks()
  setCameraState('error')
  cameraMessage.value = message
  emit('cameraError', message)
}

const stopMediaTracks = () => {
  stream?.getTracks().forEach((track) => track.stop())
  stream = null
  if (videoRef.value) {
    videoRef.value.srcObject = null
  }
}

const stopCamera = () => {
  if (animationFrameId != null) {
    cancelAnimationFrame(animationFrameId)
    animationFrameId = null
  }
  detector = null
  stopMediaTracks()
  if (!cameraVisible.value) {
    setCameraState('idle')
  }
}

onBeforeUnmount(stopCamera)
</script>

<style scoped>
.barcode-scan-field {
  width: min(100%, 440px);
}

.barcode-scan-field :deep(.el-input__wrapper) {
  min-height: 40px;
}

.barcode-scan-field :deep(.el-input-group__append) {
  padding: 0;
}

.barcode-scan-field__camera {
  width: 44px;
  height: 40px;
  padding: 0;
  border-radius: 0 4px 4px 0;
  transition-property: scale, background-color;
  transition-duration: 150ms;
  transition-timing-function: ease-out;
}

.barcode-scan-field__camera:active:not(:disabled) {
  scale: 0.96;
}

.barcode-camera {
  position: relative;
  display: grid;
  place-items: center;
  width: 100%;
  aspect-ratio: 4 / 3;
  overflow: hidden;
  border-radius: 6px;
  background: #151719;
  box-shadow:
    0 0 0 1px rgba(0, 0, 0, 0.08),
    0 2px 8px rgba(0, 0, 0, 0.12);
}

.barcode-camera__video {
  width: 100%;
  height: 100%;
  object-fit: cover;
  outline: 1px solid rgba(255, 255, 255, 0.1);
  outline-offset: -1px;
}

.barcode-camera__status {
  display: flex;
  align-items: center;
  gap: 10px;
  max-width: 360px;
  padding: 24px;
  color: #f5f7fa;
  line-height: 1.6;
  text-align: left;
  text-wrap: pretty;
}

.barcode-camera__status .el-icon {
  flex: 0 0 auto;
  font-size: 22px;
}

.barcode-camera__status--warning {
  color: #f4c66a;
}

@media (max-width: 640px) {
  .barcode-camera__status {
    padding: 18px;
  }
}
</style>
