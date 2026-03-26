import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useBottles, useDeleteBottle } from '../hooks/useBottles';
import BottleCard from '../components/bottles/BottleCard';
import BottleFilters from '../components/bottles/BottleFilters';
import ConfirmDialog from '../components/common/ConfirmDialog';
import LoadingSpinner from '../components/common/LoadingSpinner';
import { Plus, Wine } from 'lucide-react';
import type { BottleStatus } from '../types/bottle';

export default function InventoryPage() {
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState<BottleStatus | ''>('');
  const [type, setType] = useState('');
  const [deleteId, setDeleteId] = useState<string | null>(null);

  const { data, isLoading, error } = useBottles({
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

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">My Collection</h1>
          <p className="text-sm text-gray-500 mt-1">
            {data ? `${data.totalElements} bottle${data.totalElements !== 1 ? 's' : ''}` : ''}
          </p>
        </div>
        <Link
          to="/inventory/add"
          className="flex items-center gap-2 px-4 py-2 bg-primary-600 text-white text-sm font-medium rounded-md hover:bg-primary-700 transition-colors"
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

      {isLoading && <LoadingSpinner className="py-20" />}

      {error && (
        <div className="text-center py-20 text-red-600">
          Failed to load bottles. Please try again.
        </div>
      )}

      {data && data.content.length === 0 && (
        <div className="text-center py-20">
          <Wine className="w-12 h-12 text-gray-300 mx-auto" />
          <h3 className="mt-4 text-lg font-medium text-gray-900">No bottles yet</h3>
          <p className="mt-1 text-sm text-gray-500">
            Start building your collection by adding your first bottle.
          </p>
          <Link
            to="/inventory/add"
            className="inline-flex items-center gap-2 mt-4 px-4 py-2 bg-primary-600 text-white text-sm font-medium rounded-md hover:bg-primary-700"
          >
            <Plus className="w-4 h-4" />
            Add Your First Bottle
          </Link>
        </div>
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
