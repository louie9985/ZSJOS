export type AvatarVariant = 'geometric' | 'abstract' | 'character' | 'collection';
export type AvatarTheme = 'light' | 'dark';
export type AvatarVersion = 'v1' | 'v2';
export interface AvatarOptions {
  seed: string | number;
  version?: AvatarVersion;
  variant?: AvatarVariant;
  namespace?: string;
  theme?: AvatarTheme;
  size?: number;
}
export interface GeneratedAvatar {
  readonly version: AvatarVersion;
  readonly seed: string;
  readonly variant: AvatarVariant;
  readonly size: number;
  readonly theme: AvatarTheme;
  readonly palette: string;
  readonly collectionName: string | null;
  readonly svg: string;
  readonly dataUri: string;
}
export const VERSION: 'v1';
export const AVATAR_VERSIONS: readonly AvatarVersion[];
export const VARIANTS: readonly AvatarVariant[];
export function createAvatar(options: AvatarOptions): GeneratedAvatar;
