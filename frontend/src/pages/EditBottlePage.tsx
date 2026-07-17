import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  useBottle,
  useUpdateBottle,
  useUploadBottleImage,
  useDeleteBottleImage,
} from '../hooks/useBottles';
import LoadingSpinner from '../components/common/LoadingSpinner';
import BottleImage from '../components/bottles/BottleImage';
import InlineError from '../components/common/InlineError';
import type { BottleStatus } from '../types/bottle';
import { ArrowLeft, Trash2 } from 'lucide-react';

export default function EditBottlePage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { data: bottle, isLoading } = useBottle(id!);
  const updateBottle = useUpdateBottle();
  const uploadImage = useUploadBottleImage();
  const deleteImage = useDeleteBottleImage();

  const [status, setStatus] = useState<BottleStatus>('UNOPENED');
  const [percentageLeft, setPercentageLeft] = useState(100);
  const [purchaseDate, setPurchaseDate] = useState('');
  const [purchaseLocation, setPurchaseLocation] = useState('');
  const [purchaseCost, setPurchaseCost] = useState('');
  const [notes, setNotes] = useState('');
  const [rating, setRating] = useState('');
  const [storageLocation, setStorageLocation] = useState('');
  const [error, setError] = useState('');

  // Populate the form from the fetched bottle when it first loads (and again if
  // a different bottle id is loaded into this mounted component). Done during
  // render — React's "adjust state when a value changes" pattern — instead of an
  // effect, which avoids react-hooks/set-state-in-effect. Guarding on id (rather
  // than the bottle object reference the old effect used) also means a
  // background refetch of the same bottle no longer clobbers unsaved edits.
  const [loadedId, setLoadedId] = useState<string | null>(null);
  if (bottle && bottle.id !== loadedId) {
    setLoadedId(bottle.id);
    setStatus(bottle.status);
    setPercentageLeft(bottle.percentageLeft);
    setPurchaseDate(bottle.purchaseDate || '');
    setPurchaseLocation(bottle.purchaseLocation || '');
    setPurchaseCost(bottle.purchaseCost != null ? String(bottle.purchaseCost) : '');
    setNotes(bottle.notes || '');
    setRating(bottle.rating != null ? String(bottle.rating) : '');
    setStorageLocation(bottle.storageLocation || '');
  }

  if (isLoading) return <LoadingSpinner className="py-20" />;
  if (!bottle) return <div className="text-center py-20 text-text-mid">Bottle not found</div>;

  const handleImageChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setError('');
    try {
      await uploadImage.mutateAsync({ id: id!, file });
    } catch {
      setError('Image upload failed. Check that the file is JPEG/PNG/WebP and under 5 MB.');
    } finally {
      e.target.value = '';
    }
  };

  const handleRemoveImage = async () => {
    setError('');
    try {
      await deleteImage.mutateAsync(id!);
    } catch {
      setError('Failed to remove image');
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    try {
      await updateBottle.mutateAsync({
        id: id!,
        data: {
          status,
          percentageLeft,
          purchaseDate: purchaseDate || undefined,
          purchaseLocation: purchaseLocation || undefined,
          purchaseCost: purchaseCost ? Number(purchaseCost) : undefined,
          notes: notes || undefined,
          rating: rating ? Number(rating) : undefined,
          storageLocation: storageLocation || undefined,
        },
      });
      navigate('/inventory');
    } catch {
      setError('Failed to update bottle');
    }
  };

  return (
    <div className="max-w-2xl mx-auto">
      <button
        onClick={() => navigate('/inventory')}
        className="flex items-center gap-1 text-sm text-text-mid hover:text-text-hi mb-4"
      >
        <ArrowLeft className="w-4 h-4" />
        Back to Inventory
      </button>

      <h1 className="font-display text-2xl font-bold text-text-hi mb-2">Edit Bottle</h1>
      <p className="text-sm text-text-mid mb-6">
        {bottle.product.brand.name} - {bottle.product.name}
      </p>

      {error && (
        <InlineError className="mb-4">{error}</InlineError>
      )}

      <form onSubmit={handleSubmit} className="space-y-6">
        <fieldset className="border border-border rounded-lg p-4">
          <legend className="text-sm font-semibold text-text-hi px-2">Photo</legend>
          <div className="flex items-start gap-4">
            <BottleImage
              bottleId={bottle.id}
              hasImage={!!bottle.imagePath}
              className="w-32 h-32 object-cover rounded-md border border-border"
            />
            <div className="flex-1 space-y-2">
              <input
                type="file"
                accept="image/jpeg,image/png,image/webp"
                onChange={handleImageChange}
                disabled={uploadImage.isPending}
                className="w-full text-sm text-text-mid file:mr-3 file:py-2 file:px-3 file:rounded-md file:border-0 file:text-sm file:bg-primary/15 file:text-primary-bright hover:file:bg-primary/25"
              />
              <p className="text-xs text-text-mid">JPEG, PNG, or WebP. Max 5 MB.</p>
              {bottle.imagePath && (
                <button
                  type="button"
                  onClick={handleRemoveImage}
                  disabled={deleteImage.isPending}
                  className="flex items-center gap-1 text-xs text-text-mid hover:text-primary-bright disabled:opacity-50"
                >
                  <Trash2 className="w-3 h-3" />
                  Remove photo
                </button>
              )}
            </div>
          </div>
        </fieldset>

        <fieldset className="border border-border rounded-lg p-4">
          <legend className="text-sm font-semibold text-text-hi px-2">Bottle Details</legend>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-sm font-medium text-text-mid mb-1">Status</label>
              <select
                value={status}
                onChange={(e) => setStatus(e.target.value as BottleStatus)}
                className="w-full px-3 py-2 bg-surface border border-border rounded-md text-sm text-text-hi placeholder:text-text-low focus:outline-none focus:ring-2 focus:ring-primary-bright focus:border-primary-bright"
              >
                <option value="UNOPENED">Unopened</option>
                <option value="OPENED">Opened</option>
                <option value="EMPTY">Empty</option>
              </select>
            </div>
            <div>
              <label className="block text-sm font-medium text-text-mid mb-1">
                Remaining ({percentageLeft}%)
              </label>
              {/*
                Dragging snaps to 10% (manual level is an eyeball estimate), but
                the thumb can still rest on any value so a precise figure from a
                logged pour (e.g. 56%) shows where it actually is instead of
                jumping to the nearest step. Snapping lives in onChange, not the
                `step` attribute, which would force the thumb off the real value.
              */}
              <input
                type="range"
                min="0"
                max="100"
                value={percentageLeft}
                onChange={(e) => setPercentageLeft(Math.round(Number(e.target.value) / 10) * 10)}
                className="w-full mt-2 accent-primary"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-text-mid mb-1">Rating</label>
              <select
                value={rating}
                onChange={(e) => setRating(e.target.value)}
                className="w-full px-3 py-2 bg-surface border border-border rounded-md text-sm text-text-hi placeholder:text-text-low focus:outline-none focus:ring-2 focus:ring-primary-bright focus:border-primary-bright"
              >
                <option value="">No rating</option>
                {[1, 2, 3, 4, 5].map((r) => (
                  <option key={r} value={r}>{'*'.repeat(r)} ({r}/5)</option>
                ))}
              </select>
            </div>
            <div>
              <label className="block text-sm font-medium text-text-mid mb-1">Purchase Date</label>
              <input
                type="date"
                value={purchaseDate}
                onChange={(e) => setPurchaseDate(e.target.value)}
                className="w-full px-3 py-2 bg-surface border border-border rounded-md text-sm text-text-hi placeholder:text-text-low focus:outline-none focus:ring-2 focus:ring-primary-bright focus:border-primary-bright"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-text-mid mb-1">Purchase Cost</label>
              <input
                type="number"
                step="0.01"
                value={purchaseCost}
                onChange={(e) => setPurchaseCost(e.target.value)}
                placeholder="0.00"
                className="w-full px-3 py-2 bg-surface border border-border rounded-md text-sm text-text-hi placeholder:text-text-low focus:outline-none focus:ring-2 focus:ring-primary-bright focus:border-primary-bright"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-text-mid mb-1">Purchase Location</label>
              <input
                type="text"
                value={purchaseLocation}
                onChange={(e) => setPurchaseLocation(e.target.value)}
                className="w-full px-3 py-2 bg-surface border border-border rounded-md text-sm text-text-hi placeholder:text-text-low focus:outline-none focus:ring-2 focus:ring-primary-bright focus:border-primary-bright"
              />
            </div>
            <div className="col-span-2">
              <label className="block text-sm font-medium text-text-mid mb-1">Storage Location</label>
              <input
                type="text"
                value={storageLocation}
                onChange={(e) => setStorageLocation(e.target.value)}
                className="w-full px-3 py-2 bg-surface border border-border rounded-md text-sm text-text-hi placeholder:text-text-low focus:outline-none focus:ring-2 focus:ring-primary-bright focus:border-primary-bright"
              />
            </div>
            <div className="col-span-2">
              <label className="block text-sm font-medium text-text-mid mb-1">Notes</label>
              <textarea
                value={notes}
                onChange={(e) => setNotes(e.target.value)}
                rows={3}
                className="w-full px-3 py-2 bg-surface border border-border rounded-md text-sm text-text-hi placeholder:text-text-low focus:outline-none focus:ring-2 focus:ring-primary-bright focus:border-primary-bright"
              />
            </div>
          </div>
        </fieldset>

        <div className="flex justify-end gap-3">
          <button
            type="button"
            onClick={() => navigate('/inventory')}
            className="px-4 py-2 text-sm font-medium text-text-mid bg-transparent border border-border rounded-md hover:bg-surface-2 hover:text-text-hi"
          >
            Cancel
          </button>
          <button
            type="submit"
            disabled={updateBottle.isPending}
            className="px-6 py-2 bg-primary text-on-primary text-sm font-medium rounded-md hover:bg-primary-bright disabled:opacity-50"
          >
            {updateBottle.isPending ? 'Saving...' : 'Save Changes'}
          </button>
        </div>
      </form>
    </div>
  );
}
