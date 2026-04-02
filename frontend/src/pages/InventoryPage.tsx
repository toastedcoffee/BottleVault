import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useBottles, useDeleteBottle } from '../hooks/useBottles';
import BottleCard from '../components/bottles/BottleCard';
import BottleFilters from '../components/bottles/BottleFilters';
import ConfirmDialog from '../components/common/ConfirmDialog';
import SkeletonCard from '../components/common/SkeletonCard';
import EmptyState from '../components/common/EmptyState';
import ErrorState from '../components/common/ErrorState';
import { Plus, Wine, Search } from 'lucide-react';
import type { BottleStatus } from '../types/bottle';

export default function InventoryPage() {
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState<BottleStatus | ''>('');
  const [type, setType] = useState('');
  const [deleteId, setDeleteId] = useState<string | null>(null);

  const { data, isLoading, error, refetch } = useBottles({
    search: search || undefined,
    status: status || undefined,
    type: type || undefined,
    size: 50,
  });

  const deleteMutation = useDeleteBottle();

  const handleDelete = () => {
    if (deleteId) {
      deleteMutation.mutate(deleteId, {
        onSuccess: () => setDeleteId(null),
      });
    }
  };

  const hasFilters = !!(search || status || type);

  return (
    <div>
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">My Collection</h1>
          <p className="text-sm text-gray-500 mt-1">
            {data ? `${data.totalElements} bottle${data.totalElements !== 1 ? 's' : ''}` : ''}
          </p>
        </div>
        <Link
          to="/inventory/add"
          className="flex items-center justify-center gap-2 px-4 py-2 bg-primary-600 text-white text-sm font-medium rounded-md hover:bg-primary-700 transition-colors min-h-[44px]"
        >
          <Plus className="w-4 h-4" />
          Add Bottle
        </Link>
      </div>

      <div className="mb-6">
        <BottleFilters
          search={search}
          status={status}
          type={type}
          onSearchChange={setSearch}
          onStatusChange={setStatus}
          onTypeChange={setType}
        />
      </div>

      {isLoading && <SkeletonCard count={6} />}

      {error && (
        <ErrorState
          message="Failed to load your collection"
          onRetry={() => refetch()}
        />
      )}

      {data && data.content.length === 0 && !hasFilters && (
        <EmptyState
          icon={Wine}
          title="No bottles yet"
          description="Start building your collection by adding your first bottle."
          action={{ label: "Add Your First Bottle", to: "/inventory/add" }}
        />
      )}

      {data && data.content.length === 0 && hasFilters && (
        <EmptyState
          icon={Search}
          title="No results found"
          description="Try adjusting your search or filters."
        />
      )}

      {data && data.content.length > 0 && (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {data.content.map((bottle) => (
            <BottleCard
              key={bottle.id}
              bottle={bottle}
              onDelete={(id) => setDeleteId(id)}
            />
          ))}
        </div>
      )}

      <ConfirmDialog
        open={!!deleteId}
        title="Delete Bottle"
        message="Are you sure you want to remove this bottle from your collection? This action cannot be undone."
        onConfirm={handleDelete}
        onCancel={() => setDeleteId(null)}
      />
    </div>
  );
}
