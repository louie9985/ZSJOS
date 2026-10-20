import { pathToFileURL } from 'node:url'
import { createRequire } from 'node:module'
import { mkdirSync, writeFileSync as writeRaw } from 'node:fs'
import { resolve } from 'node:path'
const root = resolve(import.meta.dirname, '../../..').replaceAll('\\', '/')
const out = resolve(root, 'backend/target/personal-navigation-verification/browser')
mkdirSync(out, { recursive: true })
const writeFileSync = (path, content) => writeRaw(path, content.replaceAll('D:/ZSJ-OS', root))
const reqAdmin = createRequire(root + '/frontend/admin/package.json')
const reqWorkbench = createRequire(root + '/frontend/workbench/package.json')
const { build: viteBuild } = await import(pathToFileURL(reqAdmin.resolve('vite')).href)
const { default: vue } = await import(pathToFileURL(reqAdmin.resolve('@vitejs/plugin-vue')).href)
const { default: autoImport } = await import(pathToFileURL(reqAdmin.resolve('unplugin-auto-import/vite')).href)
const { build } = await import(pathToFileURL(reqWorkbench.resolve('esbuild')).href)
const nodes = [{id:1,name:'系统管理',children:[{id:3,name:'用户管理'},{id:4,name:'角色管理'}]},{id:2,name:'客资管理',children:[]}]
writeFileSync(out+'/fixture.js', 'export const menus='+JSON.stringify(nodes)+'; export const permissions=["zsjos:navigation-preference:query","zsjos:navigation-preference:update"];')
writeFileSync(out+'/api.js', 'export async function getNavigationPreference(){const r=await fetch("/fixture/preference");if(!r.ok)throw Error("load");return r.json()} export async function saveNavigationPreference(data){const r=await fetch("/fixture/preference",{method:"PUT",body:JSON.stringify(data)});if(!r.ok)throw Error("save");return true}')
writeFileSync(out+'/permission.js', 'import {menus} from "./fixture.js"; export const usePermissionStore=()=>({navigationSource:menus});')
writeFileSync(out+'/user.js', 'import {permissions} from "./fixture.js"; export const useUserStore=()=>({getPermissions:new Set(location.search.includes("denied")?[]:permissions)});')
writeFileSync(out+'/admin.ts', `import {createApp,ref} from "vue";import ElementPlus from "element-plus";import "element-plus/dist/index.css";import Dialog from "D:/ZSJ-OS/frontend/admin/src/layout/components/Setting/src/components/NavigationOrderDialog.vue";createApp({components:{Dialog},setup(){return {open:ref(false)}},template:'<button @click="open=true">导航栏调整</button><Dialog v-model="open"/>'}).use(ElementPlus).mount("#root")`)
writeFileSync(out+'/admin.html','<meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"><div id="root"></div><script type="module" src="./admin.ts"></script>')
await viteBuild({configFile:false,base:"./",root:out,plugins:[vue(),autoImport({imports:['vue'],dts:false})],resolve:{alias:[{find:'@/store/modules/permission',replacement:out+'/permission.js'},{find:'@/store/modules/user',replacement:out+'/user.js'},{find:'@/api/zsjos/navigationPreference',replacement:out+'/api.js'},{find:'@',replacement:root+'/frontend/admin/src'},{find:'vue',replacement:reqAdmin.resolve('vue/dist/vue.esm-bundler.js')},{find:'element-plus/dist/index.css',replacement:reqAdmin.resolve('element-plus/dist/index.css')},{find:/^element-plus$/,replacement:root+'/frontend/admin/node_modules/element-plus/es/index.mjs'}]},build:{minify:false,outDir:out+'/admin',emptyOutDir:true,rollupOptions:{input:out+'/admin.html'}}})
writeFileSync(out+'/workbench.tsx','import React from "react";import{createRoot}from"react-dom/client";import{ThemeStateContext,useThemeState}from"D:/ZSJ-OS/frontend/workbench/src/components/Theme/ThemeContext.tsx";import Settings from"D:/ZSJ-OS/frontend/workbench/src/components/SettingsDrawer.tsx";import{menus,permissions}from"./fixture.js";function Harness(){const value=useThemeState();return <ThemeStateContext.Provider value={value}><Settings menus={menus} permissions={location.search.includes("denied")?[]:permissions}/></ThemeStateContext.Provider>}createRoot(document.getElementById("root")!).render(<Harness/>);')
await build({entryPoints:[out+'/workbench.tsx'],outfile:out+'/workbench/app.js',bundle:true,format:'esm',jsx:'automatic',nodePaths:[root+'/frontend/workbench/node_modules'],plugins:[{name:'fixture-api',setup(b){b.onResolve({filter:/navigationPreference$/},()=>({path:out+'/api.js'}))}}],define:{'import.meta.env':'{}','process.env.NODE_ENV':'"production"'},minify:true})
writeFileSync(out+'/workbench/index.html','<meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"><div id="root"></div><script type="module" src="./app.js"></script>')
console.log('Actual Vue dialog and React settings/modal fixture bundles ready')
