uniform bool region_locker_useHardBorder;
uniform vec4 region_locker_configGrayColor;
uniform float region_locker_configGrayAmount;

in float region_locker_grayAmount;

float region_locker_blendSoftLight(float base, float blend) {
  return blend < 0.5 ?
    2.0 * base * blend + base * base * (1.0 - 2.0 * blend) :
    sqrt(base) * (2.0 * blend - 1.0) + 2.0 * base * (1.0 - blend);
}

vec3 region_locker_blendSoftLight(vec3 base, vec3 blend, float opacity) {
  blend = vec3(
    region_locker_blendSoftLight(base.r, blend.r),
    region_locker_blendSoftLight(base.g, blend.g),
    region_locker_blendSoftLight(base.b, blend.b)
  );
  return mix(base, blend, opacity);
}

void region_locker_frag(inout vec4 color) {
  float finalGrayAmount = region_locker_grayAmount;
  if (region_locker_useHardBorder && finalGrayAmount > 0)
    finalGrayAmount = 1;
  vec3 grayColor = vec3(dot(color.rgb, vec3(0.299, 0.587, 0.114)));
  grayColor = mix(color.rgb, grayColor, region_locker_configGrayAmount);
  grayColor = region_locker_blendSoftLight(
    grayColor, region_locker_configGrayColor.rgb, region_locker_configGrayColor.a);
  color.rgb = mix(color.rgb, grayColor, finalGrayAmount);
}
