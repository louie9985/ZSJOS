import { createApp, h, ref } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import 'virtual:uno.css'
import Workspace from '../src/views/pms/kb/document/KnowledgeLibraryWorkspace.vue'
import Sidebar from '../src/views/pms/kb/document/KnowledgeLibrarySidebar.vue'
import ContentWrap from '../src/components/ContentWrap/src/ContentWrap.vue'
import Icon from '../src/components/Icon/src/Icon.vue'
import type { KnowledgeTreeNode } from '../src/views/pms/kb/document/types'

// Isolated synthetic records; production components and real DOM interaction, no business writes.
const longName = '健康-理论免试申请承诺书（三级）与申报材料说明'.repeat(3)
const initialName =
  new URLSearchParams(location.search).get('scenario') === 'medium'
    ? '健康管理师报名申请材料与证明附件说明文档.docx'
    : longName
const nodes = (): KnowledgeTreeNode[] => [
  {
    key: 'folder-1',
    entityId: 1,
    kind: 'folder',
    label: '健康管理师',
    currentUserLevel: 1,
    children: [
      {
        key: 'folder-2',
        entityId: 2,
        kind: 'folder',
        label: '安徽省合肥市申报资料目录'.repeat(4),
        currentUserLevel: 1,
        children: [
          {
            key: 'document-3',
            entityId: 3,
            kind: 'document',
            label: initialName,
            type: 4,
            currentUserLevel: 1,
            children: []
          },
          {
            key: 'document-4',
            entityId: 4,
            kind: 'document',
            label: 'A'.repeat(160) + '.docx',
            type: 3,
            currentUserLevel: 1,
            children: []
          },
          {
            key: 'document-5',
            entityId: 5,
            kind: 'document',
            label: '短名',
            type: 4,
            currentUserLevel: 1,
            children: []
          }
        ]
      }
    ]
  }
]

createApp({
  setup() {
    const tree = ref(nodes())
    const current = ref('document-3')
    const collapsed = ref(false)
    const tight = ref(false)
    const mounted = ref(true)
    const clicks = ref(0)
    const action = ref('')
    const readonly = ref(false)
    return () =>
      h('div', { style: { fontFamily: 'Arial, sans-serif', margin: '16px' } }, [
        h(
          'div',
          { style: { display: 'flex', flexWrap: 'wrap', gap: '8px', marginBottom: '16px' } },
          [
            h(
              'button',
              {
                onClick: () => {
                  collapsed.value = !collapsed.value
                }
              },
              '切换应用侧栏'
            ),
            h(
              'button',
              {
                onClick: () => {
                  tight.value = !tight.value
                }
              },
              '窄工作区'
            ),
            h(
              'button',
              {
                onClick: () => {
                  mounted.value = !mounted.value
                }
              },
              '切换知识库'
            ),
            h(
              'button',
              {
                onClick: () => {
                  tree.value[0].children[0].children[0].label = '重命名后的完整名称'.repeat(12)
                }
              },
              '重命名'
            ),
            h(
              'button',
              {
                onClick: () => {
                  tree.value = []
                }
              },
              '空目录'
            ),
            h(
              'button',
              {
                onClick: () => {
                  tree.value[0].children[0].children[0].label = '短名'
                }
              },
              '缩短名称'
            ),
            h(
              'button',
              {
                onClick: () => {
                  readonly.value = true
                  const visit = (items: KnowledgeTreeNode[]) =>
                    items.forEach((item) => {
                      item.currentUserLevel = 3
                      visit(item.children)
                    })
                  visit(tree.value)
                }
              },
              '只读'
            ),
            h('output', { id: 'clicks' }, String(clicks.value)),
            h('output', { id: 'action' }, action.value)
          ]
        ),
        h(
          'div',
          {
            style: {
              marginLeft: window.innerWidth <= 900 ? '0' : collapsed.value ? '72px' : '240px',
              maxWidth: tight.value ? '720px' : 'none'
            }
          },
          mounted.value
            ? [
                h(
                  Workspace,
                  {},
                  {
                    sidebar: () =>
                      h(
                        ContentWrap,
                        { bodyStyle: { padding: '0' }, style: { height: '100%', marginBottom: 0 } },
                        {
                          default: () =>
                            h(Sidebar, {
                              treeData: tree.value,
                              currentNodeKey: current.value,
                              activeView: 'document',
                              writeStatus: !readonly.value,
                              canCreateFolder: !readonly.value,
                              canCreateDocument: !readonly.value,
                              onNodeClick: (node: KnowledgeTreeNode) => {
                                current.value = node.key
                                clicks.value++
                              },
                              onNodeAction: (_node: KnowledgeTreeNode, command: string) => {
                                action.value = command
                              }
                            })
                        }
                      ),
                    default: () =>
                      h(
                        ContentWrap,
                        { style: { height: '100%', marginBottom: 0 } },
                        {
                          default: () =>
                            h(
                              'h2',
                              {},
                              tree.value.length
                                ? tree.value[0].children[0].children[0].label
                                : '知识库主页'
                            )
                        }
                      )
                  }
                )
              ]
            : []
        )
      ])
  }
})
  .use(ElementPlus)
  .component('Icon', Icon)
  .directive('hasPermi', {})
  .mount('#app')
