import { Link } from 'react-router-dom';
import { useStatistics } from '../hooks/useStatistics';
import SkeletonCard from '../components/common/SkeletonCard';
import EmptyState from '../components/common/EmptyState';
import ErrorState from '../components/common/ErrorState';
import { Wine, DollarSign, Star, TrendingUp, Clock, Calculator } from 'lucide-react';
import {
  PieChart, Pie, Cell, BarChart, Bar, AreaChart, Area,
  XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Legend,
} from 'recharts';
import type { BottleSummary } from '../types/statistics';

// Deep Pour chart palette — warm wine/autumn tones that read on the dark bg.
const PIE_COLORS = [
  '#E0483E', '#D9A441', '#FF6B5E', '#B5838D', '#E8985E',
  '#A56A8C', '#C9724E', '#6FA8A0', '#8E7078', '#D98A6A',
];

// Status chart fills mirror the status tokens (opened → pour-bright, etc.).
const STATUS_COLORS: Record<string, string> = {
  UNOPENED: '#C9AFB3',
  OPENED: '#FF6B5E',
  EMPTY: '#8E7078',
};

// Shared recharts theming for the dark surface.
const AXIS_TICK = { fill: '#C9AFB3', fontSize: 12 };
const GRID_STROKE = '#3A2530';
const TOOLTIP_STYLE = {
  backgroundColor: '#24121B',
  border: '1px solid #3A2530',
  borderRadius: 8,
  color: '#FBF3F0',
};
const TOOLTIP_ITEM = { color: '#C9AFB3' };
const TOOLTIP_LABEL = { color: '#FBF3F0' };

function formatCurrency(value: number) {
  return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(value);
}

function formatMonth(year: number, month: number) {
  return new Date(year, month - 1).toLocaleDateString('en-US', { month: 'short', year: 'numeric' });
}

function formatType(type: string) {
  return type.replace(/_/g, ' ');
}

export default function StatisticsPage() {
  const { data, isLoading, error, refetch } = useStatistics();

  if (isLoading) return <SkeletonCard count={4} />;

  if (error) {
    return (
      <ErrorState
        message="Failed to load statistics"
        onRetry={() => refetch()}
      />
    );
  }

  if (!data || data.totalBottles === 0) {
    return (
      <div>
        <h1 className="text-2xl font-bold text-text-hi mb-6">Statistics</h1>
        <EmptyState
          icon={Wine}
          title="No data yet"
          description="Add some bottles to your collection to see statistics."
          action={{ label: "Add Your First Bottle", to: "/inventory/add" }}
        />
      </div>
    );
  }

  const spendingData = data.spendingOverTime.map((d) => ({
    label: formatMonth(d.year, d.month),
    total: d.total,
  }));

  const typeData = data.typeDistribution.map((d) => ({
    name: formatType(d.type),
    value: d.count,
  }));

  const statusData = data.statusBreakdown.map((d) => ({
    name: d.status.charAt(0) + d.status.slice(1).toLowerCase(),
    count: d.count,
    fill: STATUS_COLORS[d.status] || '#9ca3af',
  }));

  return (
    <div>
      <h1 className="text-2xl font-bold text-text-hi mb-6">Statistics</h1>

      {/* Summary Cards */}
      <div className="grid grid-cols-2 lg:grid-cols-5 gap-4 mb-8">
        <SummaryCard
          icon={<Wine className="w-5 h-5 text-primary-bright" />}
          label="Total Bottles"
          value={String(data.totalBottles)}
        />
        <SummaryCard
          icon={<DollarSign className="w-5 h-5 text-gold" />}
          label="Collection Value"
          value={formatCurrency(data.totalValue)}
        />
        <SummaryCard
          icon={<Calculator className="w-5 h-5 text-text-mid" />}
          label="Avg Cost / Bottle"
          value={data.averageCost != null ? formatCurrency(data.averageCost) : 'N/A'}
        />
        <SummaryCard
          icon={<Star className="w-5 h-5 text-gold" />}
          label="Avg Rating"
          value={data.averageRating ? `${data.averageRating}/5` : 'N/A'}
        />
        <SummaryCard
          icon={<TrendingUp className="w-5 h-5 text-primary-bright" />}
          label="Opened"
          value={`${data.percentageOpened.toFixed(0)}%`}
        />
      </div>

      {/* Charts Row */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-8">
        {/* Type Distribution Pie */}
        <div className="bg-surface rounded-lg border border-border p-4">
          <h2 className="text-sm font-semibold text-text-hi mb-4">Collection by Type</h2>
          {typeData.length > 0 ? (
            <ResponsiveContainer width="100%" height={280}>
              <PieChart>
                <Pie
                  data={typeData}
                  cx="50%"
                  cy="50%"
                  innerRadius={60}
                  outerRadius={100}
                  paddingAngle={2}
                  dataKey="value"
                >
                  {typeData.map((_, i) => (
                    <Cell key={i} fill={PIE_COLORS[i % PIE_COLORS.length]} />
                  ))}
                </Pie>
                <Tooltip
                  formatter={(value) => [`${value} bottles`, '']}
                  contentStyle={TOOLTIP_STYLE}
                  itemStyle={TOOLTIP_ITEM}
                  labelStyle={TOOLTIP_LABEL}
                />
                <Legend
                  layout="vertical"
                  align="right"
                  verticalAlign="middle"
                  wrapperStyle={{ fontSize: '12px', color: '#C9AFB3' }}
                />
              </PieChart>
            </ResponsiveContainer>
          ) : (
            <p className="text-sm text-text-mid text-center py-12">No data</p>
          )}
        </div>

        {/* Status Breakdown Bar */}
        <div className="bg-surface rounded-lg border border-border p-4">
          <h2 className="text-sm font-semibold text-text-hi mb-4">Status Breakdown</h2>
          {statusData.length > 0 ? (
            <ResponsiveContainer width="100%" height={280}>
              <BarChart data={statusData} barSize={48}>
                <CartesianGrid strokeDasharray="3 3" vertical={false} stroke={GRID_STROKE} />
                <XAxis dataKey="name" tick={AXIS_TICK} stroke={GRID_STROKE} />
                <YAxis allowDecimals={false} tick={AXIS_TICK} stroke={GRID_STROKE} />
                <Tooltip
                  formatter={(value) => [`${value} bottles`, '']}
                  cursor={{ fill: 'rgba(255,255,255,0.04)' }}
                  contentStyle={TOOLTIP_STYLE}
                  itemStyle={TOOLTIP_ITEM}
                  labelStyle={TOOLTIP_LABEL}
                />
                <Bar dataKey="count" radius={[4, 4, 0, 0]}>
                  {statusData.map((entry, i) => (
                    <Cell key={i} fill={entry.fill} />
                  ))}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          ) : (
            <p className="text-sm text-text-mid text-center py-12">No data</p>
          )}
        </div>
      </div>

      {/* Spending Over Time */}
      <div className="bg-surface rounded-lg border border-border p-4 mb-8">
        <h2 className="text-sm font-semibold text-text-hi mb-4">Spending Over Time</h2>
        {spendingData.length > 1 ? (
          <ResponsiveContainer width="100%" height={300}>
            <AreaChart data={spendingData}>
              <CartesianGrid strokeDasharray="3 3" vertical={false} stroke={GRID_STROKE} />
              <XAxis dataKey="label" tick={AXIS_TICK} stroke={GRID_STROKE} />
              <YAxis tickFormatter={(v) => `$${v}`} tick={AXIS_TICK} stroke={GRID_STROKE} />
              <Tooltip
                formatter={(value) => [formatCurrency(value as number), 'Spent']}
                contentStyle={TOOLTIP_STYLE}
                itemStyle={TOOLTIP_ITEM}
                labelStyle={TOOLTIP_LABEL}
              />
              <Area
                type="monotone"
                dataKey="total"
                stroke="#E0483E"
                fill="#E0483E"
                fillOpacity={0.2}
                strokeWidth={2}
              />
            </AreaChart>
          </ResponsiveContainer>
        ) : (
          <p className="text-sm text-text-mid text-center py-12">
            {spendingData.length === 1
              ? `${formatCurrency(spendingData[0]!.total)} spent in ${spendingData[0]!.label}. Chart appears after multiple months of data.`
              : 'No purchase data recorded yet.'}
          </p>
        )}
      </div>

      {/* Lists Row */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Top Rated */}
        {data.topRatedBottles.length > 0 && (
          <div className="bg-surface rounded-lg border border-border p-4">
            <h2 className="text-sm font-semibold text-text-hi mb-3 flex items-center gap-1.5">
              <Star className="w-4 h-4 text-gold" />
              Top Rated
            </h2>
            <BottleList bottles={data.topRatedBottles} showRating />
          </div>
        )}

        {/* Recent Additions */}
        {data.recentAdditions.length > 0 && (
          <div className="bg-surface rounded-lg border border-border p-4">
            <h2 className="text-sm font-semibold text-text-hi mb-3 flex items-center gap-1.5">
              <Clock className="w-4 h-4 text-primary-bright" />
              Recently Added
            </h2>
            <BottleList bottles={data.recentAdditions} />
          </div>
        )}
      </div>
    </div>
  );
}

function SummaryCard({ icon, label, value }: { icon: React.ReactNode; label: string; value: string }) {
  return (
    <div className="bg-surface rounded-lg border border-border p-4">
      <div className="flex items-center gap-2 mb-1">
        {icon}
        <span className="text-xs font-medium text-text-mid">{label}</span>
      </div>
      <p className="text-xl font-bold text-text-hi">{value}</p>
    </div>
  );
}

function BottleList({ bottles, showRating }: { bottles: BottleSummary[]; showRating?: boolean }) {
  return (
    <ul className="divide-y divide-border">
      {bottles.map((b) => (
        <li key={b.id}>
          <Link
            to={`/inventory/${b.id}`}
            className="flex items-center justify-between py-2.5 hover:bg-surface-2 -mx-2 px-2 rounded-sm transition-colors"
          >
            <div className="min-w-0">
              <p className="text-sm font-medium text-text-hi truncate">{b.productName}</p>
              <p className="text-xs text-text-mid">{b.brandName} &middot; {formatType(b.type)}</p>
            </div>
            <div className="text-right shrink-0 ml-3">
              {showRating && b.rating && (
                <span className="flex items-center gap-0.5 text-sm font-medium text-gold">
                  <Star className="w-3.5 h-3.5 fill-gold text-gold" />
                  {b.rating}/5
                </span>
              )}
              {!showRating && b.purchaseCost && (
                <span className="text-sm text-text-mid">{formatCurrency(b.purchaseCost)}</span>
              )}
            </div>
          </Link>
        </li>
      ))}
    </ul>
  );
}
