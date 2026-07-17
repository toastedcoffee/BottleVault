import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useBottle, useDeleteBottle } from '../hooks/useBottles';
import StatusBadge from '../components/bottles/StatusBadge';
import BottleImage from '../components/bottles/BottleImage';
import LogPour from '../components/bottles/LogPour';
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
  if (!bottle) return <div className="text-center py-20 text-text-mid">Bottle not found</div>;

  const { product } = bottle;

  return (
    <div className="max-w-2xl mx-auto">
      <button
        onClick={() => navigate('/inventory')}
        className="flex items-center gap-1 text-sm text-text-mid hover:text-text-hi mb-4"
      >
        <ArrowLeft className="w-4 h-4" />
        Back to Inventory
      </button>

      <div className="bg-surface rounded-lg border border-border overflow-hidden">
        {bottle.imagePath && (
          <BottleImage
            bottleId={bottle.id}
            hasImage
            className="w-full max-h-96 object-contain bg-bg"
            alt={product.name}
          />
        )}
        <div className="p-6">
          <div className="flex justify-between items-start">
            <div>
              <p className="text-sm font-medium text-primary-bright uppercase tracking-wide">
                {product.brand.name}
                {product.brand.country && ` - ${product.brand.country}`}
              </p>
              <h1 className="font-display text-2xl font-bold text-text-hi mt-1">{product.name}</h1>
              <div className="flex items-center gap-3 mt-2">
                <StatusBadge status={bottle.status} />
                <span className="text-sm text-text-mid">
                  {product.type.replace('_', ' ')}
                  {product.subtype && ` / ${product.subtype}`}
                </span>
              </div>
            </div>
            <div className="flex gap-2">
              <button
                onClick={() => navigate(`/inventory/${id}/edit`)}
                className="flex items-center gap-1 px-3 py-1.5 text-sm text-text-mid border border-border rounded-md hover:bg-surface-2 hover:text-text-hi"
              >
                <Edit className="w-3.5 h-3.5" />
                Edit
              </button>
              <button
                onClick={() => setShowDelete(true)}
                className="flex items-center gap-1 px-3 py-1.5 text-sm text-primary-bright border border-primary/40 rounded-md hover:bg-primary/10"
              >
                <Trash2 className="w-3.5 h-3.5" />
                Delete
              </button>
            </div>
          </div>

          {product.description && (
            <p className="mt-4 text-sm text-text-mid">{product.description}</p>
          )}

          <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 mt-6">
            {product.abv && (
              <div className="bg-surface-2 border border-border rounded-lg p-3">
                <p className="text-xs text-text-mid">ABV</p>
                <p className="text-lg font-semibold text-text-hi">{product.abv}%</p>
              </div>
            )}
            {product.size && (
              <div className="bg-surface-2 border border-border rounded-lg p-3">
                <p className="text-xs text-text-mid">Size</p>
                <p className="text-lg font-semibold text-text-hi">{product.size}</p>
              </div>
            )}
            <div className="bg-surface-2 border border-border rounded-lg p-3">
              <p className="text-xs text-text-mid">Remaining</p>
              <p className="text-lg font-semibold text-text-hi">{bottle.percentageLeft}%</p>
            </div>
            {bottle.rating && (
              <div className="bg-surface-2 border border-border rounded-lg p-3">
                <p className="text-xs text-text-mid">Rating</p>
                <p className="text-lg font-semibold text-text-hi flex items-center gap-1">
                  <Star className="w-4 h-4 text-gold fill-gold" />
                  {bottle.rating}/5
                </p>
              </div>
            )}
          </div>

          <LogPour key={bottle.id} bottle={bottle} />

          <div className="mt-6 space-y-3">
            {bottle.purchaseCost && (
              <div className="flex items-center gap-2 text-sm">
                <DollarSign className="w-4 h-4 text-text-low" />
                <span className="text-text-mid">Purchased for ${Number(bottle.purchaseCost).toFixed(2)}</span>
              </div>
            )}
            {bottle.purchaseDate && (
              <div className="flex items-center gap-2 text-sm">
                <Calendar className="w-4 h-4 text-text-low" />
                <span className="text-text-mid">Purchased on {bottle.purchaseDate}</span>
              </div>
            )}
            {bottle.purchaseLocation && (
              <div className="flex items-center gap-2 text-sm">
                <MapPin className="w-4 h-4 text-text-low" />
                <span className="text-text-mid">From {bottle.purchaseLocation}</span>
              </div>
            )}
            {bottle.storageLocation && (
              <div className="flex items-center gap-2 text-sm">
                <MapPin className="w-4 h-4 text-text-low" />
                <span className="text-text-mid">Stored in {bottle.storageLocation}</span>
              </div>
            )}
          </div>

          {bottle.notes && (
            <div className="mt-6 p-4 bg-gold/10 border border-gold/30 rounded-lg">
              <p className="text-xs font-medium text-gold mb-1">Notes</p>
              <p className="text-sm text-text-hi">{bottle.notes}</p>
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
