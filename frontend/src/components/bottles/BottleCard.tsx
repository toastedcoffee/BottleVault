// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
import { useNavigate } from 'react-router-dom';
import type { BottleResponse } from '../../types/bottle';
import StatusDropdown from './StatusDropdown';
import BottleImage from './BottleImage';
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
      className="bg-surface rounded-lg border border-border hover:border-primary/40 hover:bg-surface-2 transition-colors cursor-pointer"
      onClick={() => navigate(`/inventory/${bottle.id}`)}
    >
      <BottleImage
        bottleId={bottle.id}
        hasImage={!!bottle.imagePath}
        className="w-full h-32 object-cover rounded-t-lg"
        alt={product.name}
      />
      <div className="p-4">
        <div className="flex justify-between items-start mb-2">
          <div className="flex-1 min-w-0">
            <p className="text-xs font-medium text-primary-bright uppercase tracking-wide truncate">
              {product.brand.name}
            </p>
            <h3 className="text-sm font-semibold text-text-hi mt-0.5 truncate">
              {product.name}
            </h3>
          </div>
          <StatusDropdown bottleId={bottle.id} currentStatus={bottle.status} />
        </div>

        <div className="flex items-center gap-3 mt-3 text-xs text-text-mid">
          <span className="inline-flex items-center gap-1 bg-surface-2 border border-border px-2 py-0.5 rounded-sm">
            {product.type.replace('_', ' ')}
          </span>
          {product.abv && (
            <span>{product.abv}% ABV</span>
          )}
          {product.size && (
            <span>{product.size}</span>
          )}
        </div>

        <div className="flex items-center gap-4 mt-3 text-xs text-text-mid">
          {bottle.rating && (
            <span className="flex items-center gap-0.5">
              <Star className="w-3 h-3 text-gold fill-gold" />
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

      <div className="border-t border-border px-4 py-1 flex justify-between items-center">
        <button
          onClick={(e) => {
            e.stopPropagation();
            navigate(`/inventory/${bottle.id}/edit`);
          }}
          className="text-xs text-text-mid hover:text-text-hi font-medium min-h-[44px] px-2 flex items-center"
        >
          Edit
        </button>
        <button
          onClick={(e) => {
            e.stopPropagation();
            onDelete(bottle.id);
          }}
          className="text-xs text-primary-bright hover:text-primary font-medium min-h-[44px] px-2 flex items-center"
        >
          Delete
        </button>
      </div>
    </div>
  );
}
