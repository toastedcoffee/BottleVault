package com.toastedcoffee.bottlevault.data.model

enum class BottleStatus {
    UNOPENED,
    OPENED,
    EMPTY,
    AVAILABLE,  // Added for UI compatibility
    CONSUMED    // Added for UI compatibility
}

enum class SyncStatus {
    SYNCED,
    PENDING_CREATE,
    PENDING_UPDATE,
    PENDING_DELETE,
    SYNC_FAILED,
    CONFLICT
}

enum class AlcoholType {
    WHISKEY, BOURBON, SCOTCH, RYE, VODKA, GIN, RUM, TEQUILA, BRANDY, COGNAC,
    WINE_RED, WINE_WHITE, WINE_ROSE, WINE_SPARKLING, WINE_DESSERT,
    BEER, IPA, STOUT, LAGER, PILSNER, WHEAT_BEER,
    LIQUEUR, AMARO, VERMOUTH, ABSINTHE, MEZCAL, SAKE, OTHER
}