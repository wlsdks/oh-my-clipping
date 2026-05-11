interface EmptyStateAction {
  label: string;
  href?: string;
  onClick?: () => void;
}

interface EmptyStateProps {
  icon?: string;
  title: string;
  description?: string;
  action?: EmptyStateAction;
}

/** 데이터가 없을 때 표시하는 공통 빈 상태 컴포넌트 */
export function EmptyState({ icon = "📭", title, description, action }: EmptyStateProps) {
  return (
    <div className="empty-state">
      <span className="empty-state-icon" aria-hidden="true">
        {icon}
      </span>
      <strong className="empty-state-title">{title}</strong>
      {description && <p className="empty-state-desc">{description}</p>}
      {action &&
        (action.href ? (
          <a href={action.href} className="btn btn-primary empty-state-action">
            {action.label}
          </a>
        ) : (
          <button type="button" className="btn btn-primary empty-state-action" onClick={action.onClick}>
            {action.label}
          </button>
        ))}
    </div>
  );
}

/** 테이블 tbody 안에서 쓰는 빈 상태 행 */
export function EmptyStateRow({
  colSpan,
  icon = "📭",
  title,
  description,
  action
}: EmptyStateProps & { colSpan: number }) {
  return (
    <tr>
      <td colSpan={colSpan} className="empty-state-cell">
        <EmptyState icon={icon} title={title} description={description} action={action} />
      </td>
    </tr>
  );
}
