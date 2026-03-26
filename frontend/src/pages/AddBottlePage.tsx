import { useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useBrands, useCreateBrand } from '../hooks/useBrands';
import { useProducts, useCreateProduct } from '../hooks/useProducts';
import { useCreateBottle } from '../hooks/useBottles';
import { useBarcodeLookup } from '../hooks/useBarcode';
import BarcodeScanner from '../components/barcode/BarcodeScanner';
import type { BottleStatus } from '../types/bottle';
import type { AlcoholType } from '../types/product';
import { Camera, ArrowLeft, Plus } from 'lucide-react';

const ALCOHOL_TYPES: AlcoholType[] = [
  'WHISKEY', 'BOURBON', 'SCOTCH', 'RYE', 'VODKA', 'GIN', 'RUM',
  'TEQUILA', 'BRANDY', 'COGNAC', 'WINE_RED', 'WINE_WHITE', 'WINE_ROSE',
  'WINE_SPARKLING', 'WINE_DESSERT', 'BEER', 'IPA', 'STOUT', 'LAGER',
  'PILSNER', 'WHEAT_BEER', 'LIQUEUR', 'AMARO', 'VERMOUTH', 'ABSINTHE',
  'MEZCAL', 'SAKE', 'OTHER',
];

export default function AddBottlePage() {
  const navigate = useNavigate();

  // Scanner state
  const [showScanner, setShowScanner] = useState(false);
  const [scanResult, setScanResult] = useState<string | null>(null);

  // Product selection
  const [selectedBrandId, setSelectedBrandId] = useState('');
  const [selectedProductId, setSelectedProductId] = useState('');
  const [isCreatingProduct, setIsCreatingProduct] = useState(false);

  // New product fields
  const [newProductName, setNewProductName] = useState('');
  const [newProductType, setNewProductType] = useState<AlcoholType>('WHISKEY');
  const [newProductBarcode, setNewProductBarcode] = useState('');
  const [newProductSubtype, setNewProductSubtype] = useState('');
  const [newProductSize, setNewProductSize] = useState('750ml');
  const [newProductAbv, setNewProductAbv] = useState('');

  // New brand
  const [isCreatingBrand, setIsCreatingBrand] = useState(false);
  const [newBrandName, setNewBrandName] = useState('');
  const [newBrandCountry, setNewBrandCountry] = useState('');

  // Bottle fields
  const [status, setStatus] = useState<BottleStatus>('UNOPENED');
  const [purchaseDate, setPurchaseDate] = useState('');
  const [purchaseLocation, setPurchaseLocation] = useState('');
  const [purchaseCost, setPurchaseCost] = useState('');
  const [notes, setNotes] = useState('');
  const [rating, setRating] = useState('');
  const [storageLocation, setStorageLocation] = useState('');

  const [error, setError] = useState('');

  // Queries
  const { data: brands } = useBrands();
  const { data: products } = useProducts(selectedBrandId ? { brandId: selectedBrandId } : {});
  const createBrand = useCreateBrand();
  const createProduct = useCreateProduct();
  const createBottle = useCreateBottle();
  const barcodeLookup = useBarcodeLookup();

  const handleBarcodeScan = useCallback((barcode: string) => {
    setShowScanner(false);
    setScanResult(barcode);
    setNewProductBarcode(barcode);

    barcodeLookup.mutate(barcode, {
      onSuccess: (result) => {
        if (result.found && result.product) {
          // Product exists in our database - select it
          setSelectedBrandId(result.product.brand.id);
          setTimeout(() => setSelectedProductId(result.product!.id), 100);
          setIsCreatingProduct(false);
        } else if (result.found && result.externalProduct) {
          // Found externally - pre-fill new product form
          setIsCreatingProduct(true);
          setNewProductName(result.externalProduct.name);
          if (result.externalProduct.brandName) {
            setIsCreatingBrand(true);
            setNewBrandName(result.externalProduct.brandName);
          }
          if (result.externalProduct.abv) {
            setNewProductAbv(String(result.externalProduct.abv));
          }
          if (result.externalProduct.size) {
            setNewProductSize(result.externalProduct.size);
          }
        } else {
          // Not found anywhere - open create product form
          setIsCreatingProduct(true);
        }
      },
    });
  }, [barcodeLookup]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    try {
      let productId = selectedProductId;

      if (isCreatingProduct) {
        let brandId = selectedBrandId;

        if (isCreatingBrand) {
          if (!newBrandName.trim()) {
            setError('Brand name is required');
            return;
          }
          const brand = await createBrand.mutateAsync({ name: newBrandName, country: newBrandCountry || undefined });
          brandId = brand.id;
        }

        if (!brandId) {
          setError('Please select or create a brand');
          return;
        }
        if (!newProductName.trim()) {
          setError('Product name is required');
          return;
        }

        const product = await createProduct.mutateAsync({
          name: newProductName,
          brandId,
          type: newProductType,
          barcode: newProductBarcode || undefined,
          subtype: newProductSubtype || undefined,
          size: newProductSize || undefined,
          abv: newProductAbv ? Number(newProductAbv) : undefined,
        });
        productId = product.id;
      }

      if (!productId) {
        setError('Please select or create a product');
        return;
      }

      await createBottle.mutateAsync({
        productId,
        status,
        purchaseDate: purchaseDate || undefined,
        purchaseLocation: purchaseLocation || undefined,
        purchaseCost: purchaseCost ? Number(purchaseCost) : undefined,
        notes: notes || undefined,
        rating: rating ? Number(rating) : undefined,
        storageLocation: storageLocation || undefined,
      });

      navigate('/inventory');
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Failed to add bottle';
      setError(message);
    }
  };

  const isSaving = createBrand.isPending || createProduct.isPending || createBottle.isPending;

  return (
    <div className="max-w-2xl mx-auto">
      <button
        onClick={() => navigate('/inventory')}
        className="flex items-center gap-1 text-sm text-gray-600 hover:text-gray-900 mb-4"
      >
        <ArrowLeft className="w-4 h-4" />
        Back to Inventory
      </button>

      <h1 className="text-2xl font-bold text-gray-900 mb-6">Add Bottle</h1>

      {error && (
        <div className="mb-4 p-3 bg-red-50 border border-red-200 rounded-md text-sm text-red-700">{error}</div>
      )}

      {/* Barcode Scanner Button */}
      <div className="mb-6 p-4 bg-primary-50 border border-primary-200 rounded-lg">
        <div className="flex items-center justify-between">
          <div>
            <h3 className="font-medium text-primary-900">Quick Add with Barcode</h3>
            <p className="text-sm text-primary-700 mt-0.5">Scan the barcode to auto-fill product details</p>
          </div>
          <button
            onClick={() => setShowScanner(true)}
            className="flex items-center gap-2 px-4 py-2 bg-primary-600 text-white text-sm font-medium rounded-md hover:bg-primary-700"
          >
            <Camera className="w-4 h-4" />
            Scan
          </button>
        </div>
        {scanResult && (
          <p className="text-xs text-primary-600 mt-2">
            Scanned: {scanResult}
            {barcodeLookup.isPending && ' - Looking up...'}
            {barcodeLookup.isSuccess && barcodeLookup.data.found && ' - Found!'}
            {barcodeLookup.isSuccess && !barcodeLookup.data.found && ' - Not found. Fill in details below.'}
          </p>
        )}
      </div>

      <form onSubmit={handleSubmit} className="space-y-6">
        {/* Product Selection */}
        <fieldset className="border border-gray-200 rounded-lg p-4">
          <legend className="text-sm font-semibold text-gray-700 px-2">Product</legend>

          {!isCreatingProduct ? (
            <div className="space-y-3">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Brand</label>
                <select
                  value={selectedBrandId}
                  onChange={(e) => { setSelectedBrandId(e.target.value); setSelectedProductId(''); }}
                  className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm"
                >
                  <option value="">Select a brand...</option>
                  {brands?.map((b) => (
                    <option key={b.id} value={b.id}>{b.name}</option>
                  ))}
                </select>
              </div>

              {selectedBrandId && (
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Product</label>
                  <select
                    value={selectedProductId}
                    onChange={(e) => setSelectedProductId(e.target.value)}
                    className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm"
                  >
                    <option value="">Select a product...</option>
                    {products?.map((p) => (
                      <option key={p.id} value={p.id}>
                        {p.name} {p.size ? `(${p.size})` : ''}
                      </option>
                    ))}
                  </select>
                </div>
              )}

              <button
                type="button"
                onClick={() => setIsCreatingProduct(true)}
                className="flex items-center gap-1 text-sm text-primary-600 hover:text-primary-800"
              >
                <Plus className="w-3 h-3" />
                Add new product
              </button>
            </div>
          ) : (
            <div className="space-y-3">
              {/* Brand selection or creation */}
              {!isCreatingBrand ? (
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Brand</label>
                  <select
                    value={selectedBrandId}
                    onChange={(e) => setSelectedBrandId(e.target.value)}
                    className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm"
                  >
                    <option value="">Select a brand...</option>
                    {brands?.map((b) => (
                      <option key={b.id} value={b.id}>{b.name}</option>
                    ))}
                  </select>
                  <button
                    type="button"
                    onClick={() => setIsCreatingBrand(true)}
                    className="flex items-center gap-1 mt-1 text-sm text-primary-600 hover:text-primary-800"
                  >
                    <Plus className="w-3 h-3" />
                    Add new brand
                  </button>
                </div>
              ) : (
                <div className="space-y-2">
                  <label className="block text-sm font-medium text-gray-700">New Brand</label>
                  <input
                    type="text"
                    value={newBrandName}
                    onChange={(e) => setNewBrandName(e.target.value)}
                    placeholder="Brand name"
                    className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm"
                  />
                  <input
                    type="text"
                    value={newBrandCountry}
                    onChange={(e) => setNewBrandCountry(e.target.value)}
                    placeholder="Country (optional)"
                    className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm"
                  />
                  <button
                    type="button"
                    onClick={() => { setIsCreatingBrand(false); setNewBrandName(''); }}
                    className="text-sm text-gray-500 hover:text-gray-700"
                  >
                    Cancel - select existing brand
                  </button>
                </div>
              )}

              {/* New product fields */}
              <div className="grid grid-cols-2 gap-3">
                <div className="col-span-2">
                  <label className="block text-sm font-medium text-gray-700 mb-1">Product Name</label>
                  <input
                    type="text"
                    value={newProductName}
                    onChange={(e) => setNewProductName(e.target.value)}
                    placeholder="e.g. 12 Year Old Sherry Oak"
                    className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Type</label>
                  <select
                    value={newProductType}
                    onChange={(e) => setNewProductType(e.target.value as AlcoholType)}
                    className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm"
                  >
                    {ALCOHOL_TYPES.map((t) => (
                      <option key={t} value={t}>{t.replace('_', ' ')}</option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Subtype</label>
                  <input
                    type="text"
                    value={newProductSubtype}
                    onChange={(e) => setNewProductSubtype(e.target.value)}
                    placeholder="e.g. Single Malt Scotch"
                    className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Size</label>
                  <input
                    type="text"
                    value={newProductSize}
                    onChange={(e) => setNewProductSize(e.target.value)}
                    placeholder="750ml"
                    className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">ABV %</label>
                  <input
                    type="number"
                    step="0.1"
                    value={newProductAbv}
                    onChange={(e) => setNewProductAbv(e.target.value)}
                    placeholder="40.0"
                    className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm"
                  />
                </div>
                <div className="col-span-2">
                  <label className="block text-sm font-medium text-gray-700 mb-1">Barcode</label>
                  <input
                    type="text"
                    value={newProductBarcode}
                    onChange={(e) => setNewProductBarcode(e.target.value)}
                    placeholder="UPC/EAN barcode"
                    className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm"
                  />
                </div>
              </div>

              <button
                type="button"
                onClick={() => { setIsCreatingProduct(false); setNewProductName(''); }}
                className="text-sm text-gray-500 hover:text-gray-700"
              >
                Cancel - select existing product
              </button>
            </div>
          )}
        </fieldset>

        {/* Bottle Details */}
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
            <div className="col-span-2">
              <label className="block text-sm font-medium text-gray-700 mb-1">Purchase Location</label>
              <input
                type="text"
                value={purchaseLocation}
                onChange={(e) => setPurchaseLocation(e.target.value)}
                placeholder="Where did you buy it?"
                className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm"
              />
            </div>
            <div className="col-span-2">
              <label className="block text-sm font-medium text-gray-700 mb-1">Storage Location</label>
              <input
                type="text"
                value={storageLocation}
                onChange={(e) => setStorageLocation(e.target.value)}
                placeholder="e.g. Bar cabinet, Wine cellar"
                className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm"
              />
            </div>
            <div className="col-span-2">
              <label className="block text-sm font-medium text-gray-700 mb-1">Notes</label>
              <textarea
                value={notes}
                onChange={(e) => setNotes(e.target.value)}
                rows={3}
                placeholder="Tasting notes, thoughts..."
                className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm"
              />
            </div>
          </div>
        </fieldset>

        {/* Actions */}
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
            disabled={isSaving}
            className="px-6 py-2 bg-primary-600 text-white text-sm font-medium rounded-md hover:bg-primary-700 disabled:opacity-50"
          >
            {isSaving ? 'Saving...' : 'Add to Collection'}
          </button>
        </div>
      </form>

      {showScanner && (
        <BarcodeScanner onScan={handleBarcodeScan} onClose={() => setShowScanner(false)} />
      )}
    </div>
  );
}
