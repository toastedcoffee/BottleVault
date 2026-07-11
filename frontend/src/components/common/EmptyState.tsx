import { Link } from 'react-router-dom';
import type { LucideIcon } from 'lucide-react';

interface EmptyStateProps {
  icon: LucideIcon;
  title: string;
  description: string;
  action?: { label: string; to: string };
}

export default function EmptyState({ icon: Icon, title, description, action }: EmptyStateProps) {
  return (
    <div className="text-center py-16">
      <Icon className="w-12 h-12 text-text-low mx-auto" />
      <h3 className="mt-4 text-lg font-medium text-text-hi">{title}</h3>
      <p className="mt-1 text-sm text-text-mid">{description}</p>
      {action && (
        <Link
          to={action.to}
          className="mt-6 inline-flex items-center px-4 py-2 bg-primary text-on-primary text-sm font-medium rounded-lg hover:bg-primary-bright transition-colors"
        >
          {action.label}
        </Link>
      )}
    </div>
  );
}
