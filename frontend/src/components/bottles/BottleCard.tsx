import { useNavigate } from 'react-router-dom';
import type { BottleResponse } from '../../types/bottle';
import StatusBadge from './StatusBadge';
import { Star, MapPin, DollarSign } from 'lucide-react';

interface BottleCardProps {
  bottle: BottleResponse;
  onDelete: (id: string) => void;
}

export default function BottleCard({ bottle, onDelete }: BottleCardProps) {
  const navigate = useNavigate();
  const { product } = bottle;

  return (
    <div
      className="bg-white rounded-lg border border-gray-200 shadow-sm hover:shadow-md transition-shadow cursor-pointer"
      onClick={() => navigate(`/inventory/${bottle.id}`)}
    >
      <div className="p-4">
        <div className="flex justify-between items-start mb-2">
          <div className="flex-1 min-w-0">
            <p className="text-xs font-medium text-primary-600 uppercase tracking-wide truncate">
              {product.brand.name}
            </p>
            <h3 className="text-sm font-semibold text-gray-900 mt-0.5 truncate">
              {product.name}
            </h3>
          </div>
          <StatusBadge status={bottle.status} />
        </div>

        <div className="flex items-center gap-3 mt-3 text-xs text-gray-500">
          <span className="inline-flex items-center gap-1 bg-gray-100 px-2 py-0.5 rounded">
            {product.type.replace('_', ' ')}
          </span>
          {product.abv && (
            <span>{product.abv}% ABV</span>
          )}
          {product.size && (
            <span>{product.size}</span>
          )}
        </div>

        <div className="flex items-center gap-4 mt-3 text-xs text-gray-500">
          {bottle.rating && (
            <span className="flex items-center gap-0.5">
              <Star className="w-3 h-3 text-amber-500 fill-amber-500" />
              {bottle.rating}/5
            </span>
          )}
          {bottle.purchaseCost && (
            <span className="flex items-center gap-0.5">
              <DollarSign className="w-3 h-3" />
              {Number(bottle.purchaseCost).toFixed(2)}
            </span>
          )}
          {bottle.storageLocation && (
            <span className="flex items-center gap-0.5 truncate">
              <MapPin className="w-3 h-3" />
              {bottle.storageLocation}
            </span>
          )}
        </div>
      </div>

      <div className="border-t border-gray-100 px-4 py-2 flex justify-between items-center">
        <button
          onClick={(e) => {
            e.stopPropagation();
            navigate(`/inventory/${bottle.id}/edit`);
          }}
          className="text-xs text-primary-600 hover:text-primary-800 font-medium"
        >
          Edit
        </button>
        <button
          onClick={(e) => {
            e.stopPropagation();
            onDelete(bottle.id);
          }}
          className="text-xs text-red-500 hover:text-red-700 font-medium"
        >
          Delete
        </button>
      </div>
    </div>
  );
}
