import { lazy, Suspense, useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useBrands, useCreateBrand } from '../hooks/useBrands';
import { useProducts, useCreateProduct } from '../hooks/useProducts';
import { useCreateBottle, useUploadBottleImage } from '../hooks/useBottles';
import { useBarcodeLookup } from '../hooks/useBarcode';
import LoadingSpinner from '../components/common/LoadingSpinner';
import type { BottleStatus } from '../types/bottle';
import type { AlcoholType } from '../types/product';
import { Camera, ArrowLeft, Plus } from 'lucide-react';

// html5-qrcode is heavy and only needed once the user opens the scanner, so
// BarcodeScanner is loaded on demand rather than bundled with this page.
const BarcodeScanner = lazy(() => import('../components/barcode/BarcodeScanner'));

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
  const [imageFile, setImageFile] = useState<File | null>(null);
  const [imagePreview, setImagePreview] = useState<string | null>(null);

  const [error, setError] = useState('');

  // Queries
  const { data: brands } = useBrands();
  const { data: products } = useProducts(selectedBrandId ? { brandId: selectedBrandId } : {});
  const createBrand = useCreateBrand();
  const createProduct = useCreateProduct();
  const createBottle = useCreateBottle();
  const uploadImage = useUploadBottleImage();
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
      onError: () => {
        setError('Barcode lookup failed. You can fill in the product details manually.');
        setIsCreatingProduct(true);
      },
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

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

      const bottle = await createBottle.mutateAsync({
        productId,
        status,
        purchaseDate: purchaseDate || undefined,
        purchaseLocation: purchaseLocation || undefined,
        purchaseCost: purchaseCost ? Number(purchaseCost) : undefined,
        notes: notes || undefined,
        rating: rating ? Number(rating) : undefined,
        storageLocation: storageLocation || undefined,
      });

      if (imageFile) {
        try {
          await uploadImage.mutateAsync({ id: bottle.id, file: imageFile });
        } catch {
          // Bottle is already created; surface a soft warning but still navigate.
          setError('Bottle saved, but image upload failed. You can try again from the edit page.');
        }
      }

      navigate('/inventory');
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Failed to add bottle';
      setError(message);
    }
  };

  const isSaving =
    createBrand.isPending || createProduct.isPending || createBottle.isPending || uploadImage.isPending;

  const handleImageChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0] ?? null;
    setImageFile(file);
    setImagePreview((prev) => {
      if (prev) URL.revokeObjectURL(prev);
      return file ? URL.createObjectURL(file) : null;
    });
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

      <h1 className="text-2xl font-bold text-text-hi mb-6">Add Bottle</h1>

      {error && (
        <div className="mb-4 p-3 bg-primary/10 border border-primary/40 rounded-md text-sm text-primary-bright">{error}</div>
      )}

      {/* Barcode Scanner Button */}
      <div className="mb-6 p-4 bg-primary/10 border border-primary/30 rounded-lg">
        <div className="flex items-center justify-between">
          <div>
            <h3 className="font-medium text-text-hi">Quick Add with Barcode</h3>
            <p className="text-sm text-text-mid mt-0.5">Scan the barcode to auto-fill product details</p>
          </div>
          <button
            onClick={() => setShowScanner(true)}
            className="flex items-center gap-2 px-4 py-2 bg-primary text-on-primary text-sm font-medium rounded-md hover:bg-primary-bright"
          >
            <Camera className="w-4 h-4" />
            Scan
          </button>
        </div>
        {scanResult && (
          <p className="text-xs text-primary-bright mt-2">
            Scanned: {scanResult}
            {barcodeLookup.isPending && ' - Looking up...'}
            {barcodeLookup.isSuccess && barcodeLookup.data.found && ' - Found!'}
            {barcodeLookup.isSuccess && !barcodeLookup.data.found && ' - Not found. Fill in details below.'}
          </p>
        )}
      </div>

      <form onSubmit={handleSubmit} className="space-y-6">
        {/* Product Selection */}
        <fieldset className="border border-border rounded-lg p-4">
          <legend className="text-sm font-semibold text-text-hi px-2">Product</legend>

          {!isCreatingProduct ? (
            <div className="space-y-3">
              <div>
                <label className="block text-sm font-medium text-text-mid mb-1">Brand</label>
                <select
                  value={selectedBrandId}
                  onChange={(e) => { setSelectedBrandId(e.target.value); setSelectedProductId(''); }}
                  className="w-full px-3 py-2 bg-surface border border-border rounded-md text-sm text-text-hi placeholder:text-text-low focus:outline-none focus:ring-2 focus:ring-primary-bright focus:border-primary-bright"
                >
                  <option value="">Select a brand...</option>
                  {brands?.map((b) => (
                    <option key={b.id} value={b.id}>{b.name}</option>
                  ))}
                </select>
              </div>

              {selectedBrandId && (
                <div>
                  <label className="block text-sm font-medium text-text-mid mb-1">Product</label>
                  <select
                    value={selectedProductId}
                    onChange={(e) => setSelectedProductId(e.target.value)}
                    className="w-full px-3 py-2 bg-surface border border-border rounded-md text-sm text-text-hi placeholder:text-text-low focus:outline-none focus:ring-2 focus:ring-primary-bright focus:border-primary-bright"
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
                className="flex items-center gap-1 text-sm text-primary-bright hover:text-primary"
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
                  <label className="block text-sm font-medium text-text-mid mb-1">Brand</label>
                  <select
                    value={selectedBrandId}
                    onChange={(e) => setSelectedBrandId(e.target.value)}
                    className="w-full px-3 py-2 bg-surface border border-border rounded-md text-sm text-text-hi placeholder:text-text-low focus:outline-none focus:ring-2 focus:ring-primary-bright focus:border-primary-bright"
                  >
                    <option value="">Select a brand...</option>
                    {brands?.map((b) => (
                      <option key={b.id} value={b.id}>{b.name}</option>
                    ))}
                  </select>
                  <button
                    type="button"
                    onClick={() => setIsCreatingBrand(true)}
                    className="flex items-center gap-1 mt-1 text-sm text-primary-bright hover:text-primary"
                  >
                    <Plus className="w-3 h-3" />
                    Add new brand
                  </button>
                </div>
              ) : (
                <div className="space-y-2">
                  <label className="block text-sm font-medium text-text-mid">New Brand</label>
                  <input
                    type="text"
                    value={newBrandName}
                    onChange={(e) => setNewBrandName(e.target.value)}
                    placeholder="Brand name"
                    className="w-full px-3 py-2 bg-surface border border-border rounded-md text-sm text-text-hi placeholder:text-text-low focus:outline-none focus:ring-2 focus:ring-primary-bright focus:border-primary-bright"
                  />
                  <input
                    type="text"
                    value={newBrandCountry}
                    onChange={(e) => setNewBrandCountry(e.target.value)}
                    placeholder="Country (optional)"
                    className="w-full px-3 py-2 bg-surface border border-border rounded-md text-sm text-text-hi placeholder:text-text-low focus:outline-none focus:ring-2 focus:ring-primary-bright focus:border-primary-bright"
                  />
                  <button
                    type="button"
                    onClick={() => { setIsCreatingBrand(false); setNewBrandName(''); }}
                    className="text-sm text-text-mid hover:text-text-hi"
                  >
                    Cancel - select existing brand
                  </button>
                </div>
              )}

              {/* New product fields */}
              <div className="grid grid-cols-2 gap-3">
                <div className="col-span-2">
                  <label className="block text-sm font-medium text-text-mid mb-1">Product Name</label>
                  <input
                    type="text"
                    value={newProductName}
                    onChange={(e) => setNewProductName(e.target.value)}
                    placeholder="e.g. 12 Year Old Sherry Oak"
                    className="w-full px-3 py-2 bg-surface border border-border rounded-md text-sm text-text-hi placeholder:text-text-low focus:outline-none focus:ring-2 focus:ring-primary-bright focus:border-primary-bright"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-text-mid mb-1">Type</label>
                  <select
                    value={newProductType}
                    onChange={(e) => setNewProductType(e.target.value as AlcoholType)}
                    className="w-full px-3 py-2 bg-surface border border-border rounded-md text-sm text-text-hi placeholder:text-text-low focus:outline-none focus:ring-2 focus:ring-primary-bright focus:border-primary-bright"
                  >
                    {ALCOHOL_TYPES.map((t) => (
                      <option key={t} value={t}>{t.replace('_', ' ')}</option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="block text-sm font-medium text-text-mid mb-1">Subtype</label>
                  <input
                    type="text"
                    value={newProductSubtype}
                    onChange={(e) => setNewProductSubtype(e.target.value)}
                    placeholder="e.g. Single Malt Scotch"
                    className="w-full px-3 py-2 bg-surface border border-border rounded-md text-sm text-text-hi placeholder:text-text-low focus:outline-none focus:ring-2 focus:ring-primary-bright focus:border-primary-bright"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-text-mid mb-1">Size</label>
                  <input
                    type="text"
                    value={newProductSize}
                    onChange={(e) => setNewProductSize(e.target.value)}
                    placeholder="750ml"
                    className="w-full px-3 py-2 bg-surface border border-border rounded-md text-sm text-text-hi placeholder:text-text-low focus:outline-none focus:ring-2 focus:ring-primary-bright focus:border-primary-bright"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-text-mid mb-1">ABV %</label>
                  <input
                    type="number"
                    step="0.1"
                    value={newProductAbv}
                    onChange={(e) => setNewProductAbv(e.target.value)}
                    placeholder="40.0"
                    className="w-full px-3 py-2 bg-surface border border-border rounded-md text-sm text-text-hi placeholder:text-text-low focus:outline-none focus:ring-2 focus:ring-primary-bright focus:border-primary-bright"
                  />
                </div>
                <div className="col-span-2">
                  <label className="block text-sm font-medium text-text-mid mb-1">Barcode</label>
                  <input
                    type="text"
                    value={newProductBarcode}
                    onChange={(e) => setNewProductBarcode(e.target.value)}
                    placeholder="UPC/EAN barcode"
                    className="w-full px-3 py-2 bg-surface border border-border rounded-md text-sm text-text-hi placeholder:text-text-low focus:outline-none focus:ring-2 focus:ring-primary-bright focus:border-primary-bright"
                  />
                </div>
              </div>

              <button
                type="button"
                onClick={() => { setIsCreatingProduct(false); setNewProductName(''); }}
                className="text-sm text-text-mid hover:text-text-hi"
              >
                Cancel - select existing product
              </button>
            </div>
          )}
        </fieldset>

        {/* Bottle Details */}
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
            <div className="col-span-2">
              <label className="block text-sm font-medium text-text-mid mb-1">Purchase Location</label>
              <input
                type="text"
                value={purchaseLocation}
                onChange={(e) => setPurchaseLocation(e.target.value)}
                placeholder="Where did you buy it?"
                className="w-full px-3 py-2 bg-surface border border-border rounded-md text-sm text-text-hi placeholder:text-text-low focus:outline-none focus:ring-2 focus:ring-primary-bright focus:border-primary-bright"
              />
            </div>
            <div className="col-span-2">
              <label className="block text-sm font-medium text-text-mid mb-1">Storage Location</label>
              <input
                type="text"
                value={storageLocation}
                onChange={(e) => setStorageLocation(e.target.value)}
                placeholder="e.g. Bar cabinet, Wine cellar"
                className="w-full px-3 py-2 bg-surface border border-border rounded-md text-sm text-text-hi placeholder:text-text-low focus:outline-none focus:ring-2 focus:ring-primary-bright focus:border-primary-bright"
              />
            </div>
            <div className="col-span-2">
              <label className="block text-sm font-medium text-text-mid mb-1">Notes</label>
              <textarea
                value={notes}
                onChange={(e) => setNotes(e.target.value)}
                rows={3}
                placeholder="Tasting notes, thoughts..."
                className="w-full px-3 py-2 bg-surface border border-border rounded-md text-sm text-text-hi placeholder:text-text-low focus:outline-none focus:ring-2 focus:ring-primary-bright focus:border-primary-bright"
              />
            </div>
            <div className="col-span-2">
              <label className="block text-sm font-medium text-text-mid mb-1">Photo</label>
              <input
                type="file"
                accept="image/jpeg,image/png,image/webp"
                onChange={handleImageChange}
                className="w-full text-sm text-text-mid file:mr-3 file:py-2 file:px-3 file:rounded-md file:border-0 file:text-sm file:bg-primary/15 file:text-primary-bright hover:file:bg-primary/25"
              />
              <p className="text-xs text-text-mid mt-1">JPEG, PNG, or WebP. Max 5 MB.</p>
              {imagePreview && (
                <img
                  src={imagePreview}
                  alt="Preview"
                  className="mt-2 w-32 h-32 object-cover rounded-md border border-border"
                />
              )}
            </div>
          </div>
        </fieldset>

        {/* Actions */}
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
            disabled={isSaving}
            className="px-6 py-2 bg-primary text-on-primary text-sm font-medium rounded-md hover:bg-primary-bright disabled:opacity-50"
          >
            {isSaving ? 'Saving...' : 'Add to Collection'}
          </button>
        </div>
      </form>

      {showScanner && (
        <Suspense
          fallback={
            <div className="fixed inset-0 z-50 bg-black/80 flex items-center justify-center">
              <LoadingSpinner />
            </div>
          }
        >
          <BarcodeScanner onScan={handleBarcodeScan} onClose={() => setShowScanner(false)} />
        </Suspense>
      )}
    </div>
  );
}
