import { useState, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useBottle, useUpdateBottle } from '../hooks/useBottles';
import LoadingSpinner from '../components/common/LoadingSpinner';
import type { BottleStatus } from '../types/bottle';
import { ArrowLeft } from 'lucide-react';

export default function EditBottlePage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { data: bottle, isLoading } = useBottle(id!);
  const updateBottle = useUpdateBottle();

  const [status, setStatus] = useState<BottleStatus>('UNOPENED');
  const [percentageLeft, setPercentageLeft] = useState(100);
  const [purchaseDate, setPurchaseDate] = useState('');
  const [purchaseLocation, setPurchaseLocation] = useState('');
  const [purchaseCost, setPurchaseCost] = useState('');
  const [notes, setNotes] = useState('');
  const [rating, setRating] = useState('');
  const [storageLocation, setStorageLocation] = useState('');
  const [error, setError] = useState('');

  useEffect(() => {
    if (bottle) {
      setStatus(bottle.status);
      setPercentageLeft(bottle.percentageLeft);
      setPurchaseDate(bottle.purchaseDate || '');
      setPurchaseLocation(bottle.purchaseLocation || '');
      setPurchaseCost(bottle.purchaseCost != null ? String(bottle.purchaseCost) : '');
      setNotes(bottle.notes || '');
      setRating(bottle.rating != null ? String(bottle.rating) : '');
      setStorageLocation(bottle.storageLocation || '');
    }
  }, [bottle]);

  if (isLoading) return <LoadingSpinner className="py-20" />;
  if (!bottle) return <div className="text-center py-20 text-gray-500">Bottle not found</div>;

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
        className="flex items-center gap-1 text-sm text-gray-600 hover:text-gray-900 mb-4"
      >
        <ArrowLeft className="w-4 h-4" />
        Back to Inventory
      </button>

      <h1 className="text-2xl font-bold text-gray-900 mb-2">Edit Bottle</h1>
      <p className="text-sm text-gray-500 mb-6">
        {bottle.product.brand.name} - {bottle.product.name}
      </p>

      {error && (
        <div className="mb-4 p-3 bg-red-50 border border-red-200 rounded-md text-sm text-red-700">{error}</div>
      )}

      <form onSubmit={handleSubmit} className="space-y-6">
        <fieldset className="border border-gray-200 rounded-lg p-4">
          <legend className="text-sm font-semibold text-gray-700 px-2">Bottle Details</legend>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Status</label>
              <select
                value={status}
                onChange={(e) => setStatus(e.target.value as BottleStatus)}
                className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm"
              >
                <option value="UNOPENED">Unopened</option>
                <option value="OPENED">Opened</option>
                <option value="EMPTY">Empty</option>
              </select>
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Remaining ({percentageLeft}%)
              </label>
              <input
                type="range"
                min="0"
                max="100"
                value={percentageLeft}
                onChange={(e) => setPercentageLeft(Number(e.target.value))}
                className="w-full mt-2"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Rating</label>
              <select
                value={rating}
                onChange={(e) => setRating(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm"
              >
                <option value="">No rating</option>
                {[1, 2, 3, 4, 5].map((r) => (
                  <option key={r} value={r}>{'*'.repeat(r)} ({r}/5)</option>
                ))}
              </select>
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Purchase Date</label>
              <input
                type="date"
                value={purchaseDate}
                onChange={(e) => setPurchaseDate(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Purchase Cost</label>
              <input
                type="number"
                step="0.01"
                value={purchaseCost}
                onChange={(e) => setPurchaseCost(e.target.value)}
                placeholder="0.00"
                className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Purchase Location</label>
              <input
                type="text"
                value={purchaseLocation}
                onChange={(e) => setPurchaseLocation(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm"
              />
            </div>
            <div className="col-span-2">
              <label className="block text-sm font-medium text-gray-700 mb-1">Storage Location</label>
              <input
                type="text"
                value={storageLocation}
                onChange={(e) => setStorageLocation(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm"
              />
            </div>
            <div className="col-span-2">
              <label className="block text-sm font-medium text-gray-700 mb-1">Notes</label>
              <textarea
                value={notes}
                onChange={(e) => setNotes(e.target.value)}
                rows={3}
                className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm"
              />
            </div>
          </div>
        </fieldset>

        <div className="flex justify-end gap-3">
          <button
            type="button"
            onClick={() => navigate('/inventory')}
            className="px-4 py-2 text-sm font-medium text-gray-700 bg-white border border-gray-300 rounded-md hover:bg-gray-50"
          >
            Cancel
          </button>
          <button
            type="submit"
            disabled={updateBottle.isPending}
            className="px-6 py-2 bg-primary-600 text-white text-sm font-medium rounded-md hover:bg-primary-700 disabled:opacity-50"
          >
            {updateBottle.isPending ? 'Saving...' : 'Save Changes'}
          </button>
        </div>
      </form>
    </div>
  );
}
