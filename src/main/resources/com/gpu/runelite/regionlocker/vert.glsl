#define REGION_LOCKER_LOCKED_REGIONS_SIZE 16
uniform int region_locker_useGray;
uniform int region_locker_baseX;
uniform int region_locker_baseY;
uniform int region_locker_lockedRegions[REGION_LOCKER_LOCKED_REGIONS_SIZE];

out float region_locker_grayAmount;

int region_locker_toRegionId(int x, int y) {
  return (x >> 13 << 8) + (y >> 13);
}

float region_locker_isLocked(int x, int y) {
  const ivec2 regionOffsets[5] = ivec2[](
    ivec2(0, 0),
    ivec2(-1, -1),
    ivec2(-1, 1),
    ivec2(1, -1),
    ivec2(1, 1)
  );

  x = x + region_locker_baseX;
  y = y + region_locker_baseY;
  float result = 1.0;
  for (int i = 0; i < REGION_LOCKER_LOCKED_REGIONS_SIZE; ++i) {
    for (int j = 0; j < regionOffsets.length(); ++j) {
      ivec2 off = regionOffsets[j];
      int region = region_locker_toRegionId(x + off.x, y + off.y);
      result = result * (region_locker_lockedRegions[i] - region);
    }
  }
  return clamp(abs(result), 0.0, 1.0);
}

void region_locker_vert(vec3 vertex) {
  float isLocked = region_locker_isLocked(int(vertex.x), int(vertex.z));
  region_locker_grayAmount = region_locker_useGray * isLocked;
}
