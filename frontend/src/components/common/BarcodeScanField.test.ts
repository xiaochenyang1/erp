import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'

import BarcodeScanField from './BarcodeScanField.vue'

const originalMediaDevices = Object.getOwnPropertyDescriptor(navigator, 'mediaDevices')

afterEach(() => {
  vi.unstubAllGlobals()
  if (originalMediaDevices) {
    Object.defineProperty(navigator, 'mediaDevices', originalMediaDevices)
  } else {
    Reflect.deleteProperty(navigator, 'mediaDevices')
  }
  document.body.innerHTML = ''
})

const mountField = () => mount(BarcodeScanField, {
  attachTo: document.body,
  global: {
    plugins: [ElementPlus]
  }
})

const installCamera = (detect: () => Promise<Array<{ rawValue: string }>>) => {
  const stop = vi.fn()
  const mediaStream = new MediaStream()
  Object.defineProperty(mediaStream, 'getTracks', {
    configurable: true,
    value: () => [{ stop }] as unknown as MediaStreamTrack[]
  })
  const getUserMedia = vi.fn().mockResolvedValue(mediaStream)
  Object.defineProperty(navigator, 'mediaDevices', {
    configurable: true,
    value: { getUserMedia }
  })
  vi.stubGlobal('BarcodeDetector', class {
    detect = detect
  })
  return { stop, getUserMedia }
}

describe('BarcodeScanField', () => {
  it('emits one trimmed scan value when a scanner sends Enter', async () => {
    const wrapper = mountField()
    const input = wrapper.find('input')

    await input.setValue(' 6901234567890 ')
    await input.trigger('keyup.enter')

    expect(wrapper.emitted('scan')).toEqual([['6901234567890']])
    expect((input.element as HTMLInputElement).value).toBe('')
  })

  it('does not emit a scan for blank input', async () => {
    const wrapper = mountField()
    const input = wrapper.find('input')

    await input.setValue('   ')
    await input.trigger('keyup.enter')

    expect(wrapper.emitted('scan')).toBeUndefined()
  })

  it('keeps scanner input enabled when camera detection is unsupported', async () => {
    vi.stubGlobal('BarcodeDetector', undefined)
    const wrapper = mountField()

    await wrapper.find('[aria-label="打开摄像头扫码"]').trigger('click')

    expect(wrapper.text()).toContain('当前浏览器不支持摄像头识码')
    expect(wrapper.find('input').attributes('disabled')).toBeUndefined()
    expect(wrapper.emitted('cameraState')).toEqual([['unsupported']])
  })

  it('emits camera detections through the same scan event', async () => {
    const { getUserMedia } = installCamera(vi.fn().mockResolvedValueOnce([{ rawValue: 'CAMERA-6901' }]))
    const wrapper = mountField()

    await wrapper.find('[aria-label="打开摄像头扫码"]').trigger('click')
    await flushPromises()

    expect(getUserMedia).toHaveBeenCalledOnce()
    expect(wrapper.emitted('cameraError')).toBeUndefined()
    expect(wrapper.emitted('scan')).toEqual([['CAMERA-6901']])
    expect(wrapper.emitted('cameraState')).toEqual([
      ['requesting'],
      ['active'],
      ['idle']
    ])
  })

  it('stops every camera track when unmounted', async () => {
    const { stop, getUserMedia } = installCamera(() => new Promise(() => {}))
    const wrapper = mountField()

    await wrapper.find('[aria-label="打开摄像头扫码"]').trigger('click')
    await flushPromises()
    expect(getUserMedia).toHaveBeenCalledOnce()

    wrapper.unmount()

    expect(stop).toHaveBeenCalledOnce()
  })
})
