import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useBottle, useDeleteBottle } from '../hooks/useBottles';
import StatusBadge from '../components/bottles/StatusBadge';
import ConfirmDialog from '../components/common/ConfirmDialog';
import LoadingSpinner from '../components/common/LoadingSpinner';
import { ArrowLeft, Edit, Trash2, Star, MapPin, DollarSign, Calendar } from 'lucide-react';

export default function BottleDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { data: bottle, isLoading } = useBottle(id!);
  const deleteMutation = useDeleteBottle();
  const [showDelete, setShowDelete] = useState(false);

  if (isLoading) return <LoadingSpinner className="py-20" />;
  if (!bottle) return <div className="text-center py-20 text-gray-500">Bottle not found</div>;

  const { product } = bottle;

  return (
    <div className="max-w-2xl mx-auto">
      <button
        onClick={() => navigate('/inventory')}
        className="flex items-center gap-1 text-sm text-gray-600 hover:text-gray-900 mb-4"
      >
        <ArrowLeft className="w-4 h-4" />
        Back to Inventory
      </button>

      <div className="bg-white rounded-lg border border-gray-200 shadow-sm">
        <div className="p-6">
          <div className="flex justify-between items-start">
            <div>
              <p className="text-sm font-medium text-primary-600 uppercase tracking-wide">
                {product.brand.name}
                {product.brand.country && ` - ${product.brand.country}`}
              </p>
              <h1 className="text-2xl font-bold text-gray-900 mt-1">{product.name}</h1>
              <div className="flex items-center gap-3 mt-2">
                <StatusBadge status={bottle.status} />
                <span className="text-sm text-gray-500">
                  {product.type.replace('_', ' ')}
                  {product.subtype && ` / ${product.subtype}`}
                </span>
              </div>
            </div>
            <div className="flex gap-2">
              <button
                onClick={() => navigate(`/inventory/${id}/edit`)}
                className="flex items-center gap-1 px-3 py-1.5 text-sm text-primary-600 border border-primary-200 rounded-md hover:bg-primary-50"
              >
                <Edit className="w-3.5 h-3.5" />
                Edit
              </button>
              <button
                onClick={() => setShowDelete(true)}
                className="flex items-center gap-1 px-3 py-1.5 text-sm text-red-600 border border-red-200 rounded-md hover:bg-red-50"
              >
                <Trash2 className="w-3.5 h-3.5" />
                Delete
              </button>
            </div>
          </div>

          {product.description && (
            <p className="mt-4 text-sm text-gray-600">{product.description}</p>
          )}

          <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 mt-6">
            {product.abv && (
              <div className="bg-gray-50 rounded-lg p-3">
                <p className="text-xs text-gray-500">ABV</p>
                <p className="text-lg font-semibold">{product.abv}%</p>
              </div>
            )}
            {product.size && (
              <div className="bg-gray-50 rounded-lg p-3">
                <p className="text-xs text-gray-500">Size</p>
                <p className="text-lg font-semibold">{product.size}</p>
              </div>
            )}
            <div className="bg-gray-50 rounded-lg p-3">
              <p className="text-xs text-gray-500">Remaining</p>
              <p className="text-lg font-semibold">{bottle.percentageLeft}%</p>
            </div>
            {bottle.rating && (
              <div className="bg-gray-50 rounded-lg p-3">
                <p className="text-xs text-gray-500">Rating</p>
                <p className="text-lg font-semibold flex items-center gap-1">
                  <Star className="w-4 h-4 text-amber-500 fill-amber-500" />
                  {bottle.rating}/5
                </p>
              </div>
            )}
          </div>

          <div className="mt-6 space-y-3">
            {bottle.purchaseCost && (
              <div className="flex items-center gap-2 text-sm">
                <DollarSign className="w-4 h-4 text-gray-400" />
                <span className="text-gray-600">Purchased for ${Number(bottle.purchaseCost).toFixed(2)}</span>
              </div>
            )}
            {bottle.purchaseDate && (
              <div className="flex items-center gap-2 text-sm">
                <Calendar className="w-4 h-4 text-gray-400" />
                <span className="text-gray-600">Purchased on {bottle.purchaseDate}</span>
              </div>
            )}
            {bottle.purchaseLocation && (
              <div className="flex items-center gap-2 text-sm">
                <MapPin className="w-4 h-4 text-gray-400" />
                <span className="text-gray-600">From {bottle.purchaseLocation}</span>
              </div>
            )}
            {bottle.storageLocation && (
              <div className="flex items-center gap-2 text-sm">
                <MapPin className="w-4 h-4 text-gray-400" />
                <span className="text-gray-600">Stored in {bottle.storageLocation}</span>
              </div>
            )}
          </div>

          {bottle.notes && (
            <div className="mt-6 p-4 bg-amber-50 border border-amber-100 rounded-lg">
              <p className="text-xs font-medium text-amber-700 mb-1">Notes</p>
              <p className="text-sm text-amber-900">{bottle.notes}</p>
            </div>
          )}
        </div>
      </div>

      <ConfirmDialog
        open={showDelete}
        title="Delete Bottle"
        message="Are you sure you want to remove this bottle from your collection?"
        onConfirm={() => {
          deleteMutation.mutate(id!, {
            onSuccess: () => navigate('/inventory'),
          });
        }}
        onCancel={() => setShowDelete(false)}
      />
    </div>
  );
}
