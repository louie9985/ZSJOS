import { Image, Tag, Typography } from 'antd'
import type { LeadFollowUp } from '../services/api'
import { formatTimestamp } from '../services/time'
import { snapshotDisplayLabel } from '../services/leadManagement'

/**
 * Vertical timeline for follow-up records.
 *
 * Left: thin vertical track with colored dots.
 * Right: time label + full-width content card.
 *
 * Dot color reflects the record's significance:
 *  - 本轮首次跟进 → green (success)
 *  - Default → primary blue
 */

function TimelineNode({ record }: { record: LeadFollowUp }) {
  const categoryBefore = snapshotDisplayLabel(record.categoryBeforeLabel, record.categoryBefore)
  const categoryAfter = snapshotDisplayLabel(record.categoryAfterLabel, record.categoryAfter)

  return (
    <div className={`fu-node${record.firstInAssignment ? ' fu-node--first' : ''}`}>
      {/* Marker: dot on the track line */}
      <div className="fu-node-marker">
        <div className="fu-node-dot"/>
      </div>

      {/* Content: time + card */}
      <div className="fu-node-content">
        <div className="fu-node-time">{formatTimestamp(record.occurredAt)}</div>
        <div className="fu-node-card">
          <div className="fu-node-tags">
            <Tag>联系方式：{snapshotDisplayLabel(record.methodLabel, record.method)}</Tag>
            <Tag color="blue">跟进结果：{snapshotDisplayLabel(record.resultLabel, record.result)}</Tag>
            {record.firstInAssignment && <Tag color="green">本轮首次</Tag>}
          </div>

          {record.remark && (
            <Typography.Paragraph
              className="fu-node-remark"
              ellipsis={{ rows: 3, expandable: 'collapsible' }}
            >
              {record.remark}
            </Typography.Paragraph>
          )}

          {record.categoryBefore !== record.categoryAfter && (
            <span className="fu-node-meta">分类：{categoryBefore} → {categoryAfter}</span>
          )}
          {record.nextFollowUpAt && (
            <span className="fu-node-meta">下次跟进：{formatTimestamp(record.nextFollowUpAt)}</span>
          )}

          {record.images.length > 0 && (
            <Image.PreviewGroup>
              <div className="fu-node-images">
                {record.images.map(image => (
                  <Image key={image.infraFileId} src={image.url} alt={image.originalName}/>
                ))}
              </div>
            </Image.PreviewGroup>
          )}
        </div>
      </div>
    </div>
  )
}

export default function FollowUpTimeline({ records }: { records: LeadFollowUp[] }) {
  if (records.length === 0) return null

  return (
    <div className="fu-timeline">
      {records.map(record => (
        <TimelineNode key={record.id} record={record}/>
      ))}
    </div>
  )
}
