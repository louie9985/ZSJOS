# ZSJOS Workbench Foundation API

## Scope

The workbench keeps ZSJOS business tasks and BPM approval tasks as separate facts sources. React may display them together, but no `/workbench/*` aggregation endpoint or copied BPM state is introduced.

The user-facing work-plan model is:

```text
plan -> work-task tree -> completion report -> optional confirmation -> plan summary
```

Templates provide four dynamic-field sections: `plan`, `task`, `report`, and `summary`. A plan snapshots template fields at creation. Supplemental fields can be added only while the plan is a draft.

## Business tasks

- `GET /zsjos/business-task/my-summary` retains pending bucket counts.
- `GET /zsjos/business-task/my-page` retains the compatibility pending page.
- `GET /zsjos/business-task/my-task-page` pages pending or completed/cancelled tasks in the database.
- `GET /zsjos/business-task/menu-task-summary` returns the current user's pending-task counts projected onto authorized Workbench menu paths. Counts are server-computed; the response may include an urgent severity and a typed target query for deep linking. It excludes notification unread counts and completed tasks.
- Domains create, complete, and cancel tasks through `BusinessTaskCommandService`.
- Clients execute controlled `actionCode` values only; arbitrary backend URLs are never executed.

## Work-plan API

| Method and path | Purpose | Permission |
| --- | --- | --- |
| `GET /zsjos/work-plan/page` | Page visible plans | `zsjos:work-plan:query` |
| `POST /zsjos/work-plan/search-page` | Search fixed and dynamic plan-target fields | `zsjos:work-plan:query` |
| `POST /zsjos/work-plan/export-excel` | Export visible task details | `zsjos:work-plan:export` |
| `GET /zsjos/work-plan/get` | Read plan, task tree, reports, summary, and changes | `zsjos:work-plan:query` |
| `POST /zsjos/work-plan/create` | Create a draft from a required template | `zsjos:work-plan:create` |
| `PUT /zsjos/work-plan/{id}` | Edit draft or adjust active plan with reason | `zsjos:work-plan:update` |
| `POST /zsjos/work-plan/{id}/publish` | Publish directly | `zsjos:work-plan:publish` |
| `POST /zsjos/work-plan/{id}/cancel` | Cancel plan | `zsjos:work-plan:cancel` |
| `POST /zsjos/work-plan/{planId}/task` | Assign top-level or child task | `assign` or `decompose` |
| `PUT /zsjos/work-plan/task/{id}` | Adjust a task with reason | `zsjos:work-plan:assign` |
| `POST /zsjos/work-plan/task/{id}/cancel` | Cancel task and unfinished descendants | `zsjos:work-plan:cancel` |
| `POST /zsjos/work-plan/task/{id}/report` | Submit completion report | `zsjos:work-plan:complete` |
| `POST /zsjos/work-plan/task/{id}/confirm` | Confirm or return report | `zsjos:work-plan:review` |
| `POST /zsjos/work-plan/{id}/summary` | Submit summary and complete plan | `zsjos:work-plan:close` |
| `POST /zsjos/work-plan/task/temporary` | Create temporary task | `zsjos:work-plan:assign` |
| `POST /zsjos/work-plan/attachment/upload` | Upload an Infra-backed attachment | Applicable write permission |

In role management, the `工作计划` node is only the page route. Read access is granted by its separate child permission `查看工作计划` (`zsjos:work-plan:query`), independently from create, update, publish, assignment, review, cancellation, summary, and export permissions.

Work-plan summary submission is serialized with task creation on the plan row and is rejected while any task remains active; an empty task set keeps the existing summary behavior. Plan details return task change logs only for task IDs visible to the caller, while plan-level changes remain visible with the plan.

Plan states are `draft -> active -> completed/cancelled`. Drafts and published plans may contain no tasks. Completing every effective task makes `summaryReady=true` for reminder purposes, but the plan owner may submit a final summary while tasks remain unfinished and record the outstanding work in that summary.

Task states are `draft -> pending -> awaiting_confirmation -> completed`, with a return moving `awaiting_confirmation -> pending`. A task without confirmation completes immediately after its report. A parent task may report while children remain unfinished; child progress remains visible as management context. Cancelling a task affects only that task by default. Clients must explicitly submit `cascadeChildren=true` to cancel its unfinished descendants as well.

Ordinary active-plan and task edits may omit a reason. Reassignment or confirmation-responsibility changes still require a reason and remain audited. Pending business-task projections are updated in place instead of being cancelled and recreated for ordinary edits. Active plans may append supplemental fields without rebuilding their existing field snapshot.

Task deadlines are administrative targets rather than system authorization boundaries. A task may end outside the plan period or later than its parent; clients may show a non-blocking warning, and reminder or overdue processing continues to use the submitted deadline. Plan and task operations remain available after their target dates. Child assignment must submit the selected parent as `parentTaskId`; the parent must belong to the same plan, while omitting it creates a top-level task.

The plan period type is descriptive. Week and month plans may cross calendar-week or calendar-month boundaries. The only hard date-ordering rule is that the plan end date cannot be earlier than its start date.

## Authorization

Feature permission, data scope, and object action are cumulative. Data scope grants visibility, not permission to submit for an assignee or confirm for a confirmer. Every response returns server-calculated `availableActions`, and every write rechecks object permission and optimistic version at the service boundary.

## Database

The schema is owned by `V022__workbench_foundation.sql`. It contains the task tree, report history, plan summary, field snapshots, unified typed values, attachments, and immutable change records. V021 is independent feature work and only matters when the final release migration chain is verified.
# Workbench additions

The shared Lead detail includes a lazy-loaded read-only `订单记录（数量）` tab for the owner and subordinate-manager modes. It aggregates all first-purchase and repurchase orders by the Lead's Person, uses a desktop master-detail layout and a mobile order selector, caches full details by order ID for the current Lead, and clears the cache when the Lead changes. The submitter Lead detail does not render this tab or request its APIs; the backend independently requires current Lead ownership, a live manager relationship over the owner department, or `zsjos:lead:query-all`.

Business task action `OPEN_SALES_ORDER_REVISION` opens the submitter's My Orders page with the rejected order selected. The React export-task route consumes the server-owned `/zsjos/export-task` menu and the existing page, cancel and download-address APIs; it does not manufacture permissions or a static menu tree.
