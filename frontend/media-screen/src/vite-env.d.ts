/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_MEDIA_SCREEN_API_BASE_URL?: string;
  readonly VITE_MEDIA_SCREEN_API_PREFIX?: string;
  readonly VITE_MEDIA_SCREEN_BACKEND_TARGET?: string;
  readonly VITE_MEDIA_SCREEN_TENANT_ID?: string;
  readonly VITE_MEDIA_SCREEN_ENABLE_MOCK?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
