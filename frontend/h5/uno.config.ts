import { defineConfig, presetUno } from 'unocss'

export default defineConfig({
  presets: [presetUno()],
  shortcuts: {
    'page-container': 'min-h-screen bg-gray-50 pb-[50px]',
    'card': 'bg-white rounded-lg mx-4 p-4',
    'section-title': 'text-base font-medium text-gray-900 mb-3'
  }
})
