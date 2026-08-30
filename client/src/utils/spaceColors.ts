import type { SpaceType } from "../api/types";

/**
 * Fallback color per space type, shared between BoardGraph (rendering a saved board) and
 * BoardCreatePage's live preview (rendering an in-progress form) so both agree on what an
 * unassigned SHOP or a BASIC space looks like. A space belonging to a district uses the
 * district's own colorHex instead -- see buildGraphPreview and BoardGraph's board->node mapping.
 */
export const SPACE_TYPE_COLORS: Record<SpaceType, string> = {
  BASIC: "#8a8f98",
  SHOP: "#c77d2e",
  HEART: "#d1425f",
  DIAMOND: "#2e8bc7",
  SPADE: "#3f4750",
  CLUB: "#3f9142",
  BANK: "#c9a227",
};
