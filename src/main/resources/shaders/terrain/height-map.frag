#version 330

uniform mat4 modelViewProjectionMatrix;
uniform mat4 modelViewMatrix;
uniform mat3 normalMatrix;
uniform vec3 lightDirection;
uniform vec3 cameraPosition;
uniform vec4 color1;
uniform vec4 color2;
uniform vec4 color3;
uniform vec4 color4;
uniform vec4 color5;
uniform vec4 color6;
uniform vec4 lightColor;
uniform vec4 baseColor;
uniform vec4 waterColor;
uniform float roughness;
uniform float waterRoughness;
uniform float metallic;
uniform float waterMetallic;
uniform float specularIntensity;
uniform float waterSpecularIntensity;
uniform float indirectIntensity;
uniform float heightScale;
uniform float uvScale;
uniform float horizonBlend;
uniform float lightMaxFogEffect;
uniform mat3 fogParams;
uniform int renderOptions;
uniform sampler2D heightMapTexture;
uniform sampler2D riverMapTexture;
uniform sampler2D brdfMap;
uniform samplerCube irradianceMap;
uniform samplerCube specularMap;
uniform sampler2D inscatterTexture;
uniform sampler2D inscatterHorizonTexture;
uniform sampler2D lossTexture;


in VertexData {
    vec2 position;
    vec2 uv;
} VertexIn;

layout(location = 0) out vec4 colorOut;

const ivec3 off = ivec3(-1, 0, 1);

const float gamma = 2.2;

const float iGamma = 1 / gamma;

const float pi = 3.1415926538;

const float f0 = 0.08;

const float skyDistance = 100;

const float lightPower = 4;

const float distanceScale = 0.006153846;

vec3 toLinear(vec3 v) {
  return pow(v, vec3(gamma));
}

vec4 toLinear(vec4 v) {
  return vec4(toLinear(v.rgb), v.a);
}

vec3 toGamma(vec3 v) {
  return pow(v, vec3(iGamma));
}

vec4 toGamma(vec4 v) {
  return vec4(toGamma(v.rgb), v.a);
}

vec3 applyFog(vec3 worldPosition, vec3 pixelColor) {
    vec3 fogColor = fogParams[0].xyz;
    float atmosphericFogDensity = fogParams[1].x;
    float exponentialFogDensity = fogParams[1].y;
    float exponentialFogHeightFalloff = fogParams[1].z;
    float fogHeightClampPower = fogParams[2].x;
    float fogOn = fogParams[2].y;
    float heightFalloff = exponentialFogHeightFalloff / exponentialFogDensity;

    vec3 distanceVector = worldPosition - cameraPosition;
    float distance = clamp(length(distanceVector) * distanceScale, 0, 100);
    vec3 viewDir = normalize(distanceVector);

    float lDotV = clamp(dot(lightDirection, viewDir), 0, 1);

    float heightDifference = distanceVector.z * distanceScale;

    float scatterU = clamp(1 - exp(-distance * atmosphericFogDensity) + (heightFalloff * exp(-cameraPosition.z * distanceScale * exponentialFogDensity) * ((1 - exp(-distance * heightDifference * exponentialFogDensity)) / heightDifference)) * exp((cameraPosition.z * distanceScale + heightDifference) * fogHeightClampPower), 0, 1);
    float scatterV = 1 - clamp((1 + dot(viewDir, vec3(0, 0, 1))) * 0.5, 0, 1);
    vec2 scatterLookup = vec2(scatterU, scatterV);

    vec3 loss = mix(vec3(1.0), texture(lossTexture, scatterLookup).rgb, fogOn);

    vec3 fogWithLightColor = mix(vec3(0.0), mix(fogColor, lightColor.rgb * 1.3, vec3((pow(lDotV, lightPower) * lightMaxFogEffect))), fogOn);

    vec3 inscatter = mix(texture(inscatterTexture, scatterLookup).rgb, texture(inscatterHorizonTexture, scatterLookup).rgb, horizonBlend);

    return clamp(pixelColor * loss + fogWithLightColor * inscatter, 0, 1);
}

void main() {
    if (!gl_FrontFacing) {
        colorOut = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }

    float uvScale2 = uvScale * 2;
    vec2 size = vec2(uvScale2, 0.0);
    float unscaledHeight = texture(heightMapTexture, VertexIn.uv).r;
    float height = unscaledHeight * heightScale;
    float westPixel = textureOffset(heightMapTexture, VertexIn.uv, off.xy).r * heightScale;
    float eastPixel = textureOffset(heightMapTexture, VertexIn.uv, off.zy).r * heightScale;
    float northPixel = textureOffset(heightMapTexture, VertexIn.uv, off.yx).r * heightScale;
    float southPixel = textureOffset(heightMapTexture, VertexIn.uv, off.yz).r * heightScale;
    float northWestPixel = textureOffset(heightMapTexture, VertexIn.uv, off.xx).r * heightScale;
    float southEastPixel = textureOffset(heightMapTexture, VertexIn.uv, off.zz).r * heightScale;
    float northEastPixel = textureOffset(heightMapTexture, VertexIn.uv, off.zx).r * heightScale;
    float southWestPixel = textureOffset(heightMapTexture, VertexIn.uv, off.xz).r * heightScale;
    vec3 va = normalize(vec3(size.xy, eastPixel - westPixel));
    vec3 vb = normalize(vec3(size.yx, southPixel - northPixel));
    vec3 vc = normalize(vec3(size.xx, southEastPixel - northWestPixel));
    vec3 vd = normalize(vec3(size.x, -size.x, southWestPixel - northEastPixel));
    vec3 crossNormal = cross(va, vb);
    crossNormal = normalize(vec3(crossNormal.x, -crossNormal.y, crossNormal.z));
    vec3 xNormal = cross(vd, vc);
    xNormal = normalize(vec3(xNormal.y, -xNormal.x, xNormal.z));
    vec3 normal = normalize(xNormal + crossNormal);

    vec3 worldPosition = vec3(VertexIn.position.xy, height);
    float occlusion = 1 - clamp((
        max(dot(normal, normalize(vec3(-uvScale, 0.0, westPixel - height))), 0) +
        max(dot(normal, normalize(vec3(uvScale, 0.0, eastPixel - height))), 0) +
        max(dot(normal, normalize(vec3(0.0, uvScale, northPixel - height))), 0) +
        max(dot(normal, normalize(vec3(0.0, -uvScale, southPixel - height))), 0) +
        max(dot(normal, normalize(vec3(-uvScale, uvScale, northWestPixel - height))), 0) +
        max(dot(normal, normalize(vec3(uvScale, -uvScale, southEastPixel - height))), 0) +
        max(dot(normal, normalize(vec3(uvScale, uvScale, northEastPixel - height))), 0) +
        max(dot(normal, normalize(vec3(-uvScale, -uvScale, southWestPixel - height))), 0) +
        max(dot(normal, normalize(vec3(-uvScale2, -uvScale, (textureOffset(heightMapTexture, VertexIn.uv, ivec2(-2, -1)).r * heightScale) - height))), 0) +
        max(dot(normal, normalize(vec3(-uvScale2, 0, (textureOffset(heightMapTexture, VertexIn.uv, ivec2(-2, 0)).r * heightScale) - height))), 0) +
        max(dot(normal, normalize(vec3(-uvScale2, uvScale, (textureOffset(heightMapTexture, VertexIn.uv, ivec2(-2, 1)).r * heightScale) - height))), 0) +
        max(dot(normal, normalize(vec3(-uvScale, uvScale2, (textureOffset(heightMapTexture, VertexIn.uv, ivec2(-1, 2)).r * heightScale) - height))), 0) +
        max(dot(normal, normalize(vec3(0, uvScale2, (textureOffset(heightMapTexture, VertexIn.uv, ivec2(0, 2)).r * heightScale) - height))), 0) +
        max(dot(normal, normalize(vec3(uvScale, uvScale2, (textureOffset(heightMapTexture, VertexIn.uv, ivec2(1, 2)).r * heightScale) - height))), 0) +
        max(dot(normal, normalize(vec3(uvScale2, uvScale, (textureOffset(heightMapTexture, VertexIn.uv, ivec2(2, 1)).r * heightScale) - height))), 0) +
        max(dot(normal, normalize(vec3(uvScale2, 0, (textureOffset(heightMapTexture, VertexIn.uv, ivec2(2, 0)).r * heightScale) - height))), 0) +
        max(dot(normal, normalize(vec3(uvScale2, -uvScale, (textureOffset(heightMapTexture, VertexIn.uv, ivec2(2, -1)).r * heightScale) - height))), 0) +
        max(dot(normal, normalize(vec3(uvScale, -uvScale2, (textureOffset(heightMapTexture, VertexIn.uv, ivec2(1, -2)).r * heightScale) - height))), 0) +
        max(dot(normal, normalize(vec3(0, -uvScale2, (textureOffset(heightMapTexture, VertexIn.uv, ivec2(0, -2)).r * heightScale) - height))), 0) +
        max(dot(normal, normalize(vec3(-uvScale, -uvScale2, (textureOffset(heightMapTexture, VertexIn.uv, ivec2(-1, -2)).r * heightScale) - height))), 0)
    ) / 20.0, 0, 1);

    float adjustedMetallic = waterMetallic;
    float adjustedRoughness = waterRoughness;
    float adjustedSpecIntensity = waterSpecularIntensity;
    vec4 riverColor;
    vec4 lowColor;
    vec4 highColor;
    float interpolation;
    vec4 finalBaseColor;
    bool hasColor;
    bool hasRivers;
    if (renderOptions == 1) {
        hasColor = false;
        hasRivers = true;
    } else if (renderOptions == 2) {
        hasColor = true;
        hasRivers = false;
    } else if (renderOptions == 3) {
        hasColor = false;
        hasRivers = false;
    } else {
        hasColor = true;
        hasRivers = true;
    }
    if (hasColor) {
        if (unscaledHeight <= 0.05) {
            lowColor = color1;
            highColor = color1;
            interpolation = 0.5;
        } else if (unscaledHeight <= 0.19) {
            lowColor = color1;
            highColor = color2;
            interpolation = (unscaledHeight - 0.05) * 7.1426;
        } else if (unscaledHeight <= 0.24) {
            lowColor = color2;
            highColor = color2;
            interpolation = 0.5;
        } else if (unscaledHeight <= 0.38) {
            lowColor = color2;
            highColor = color3;
            interpolation = (unscaledHeight - 0.24) * 7.1426;
        } else if (unscaledHeight <= 0.43) {
            lowColor = color3;
            highColor = color3;
            interpolation = 0.5;
        } else if (unscaledHeight <= 0.57) {
            lowColor = color3;
            highColor = color4;
            interpolation = (unscaledHeight - 0.43) * 7.1426;
        } else if (unscaledHeight <= 0.62) {
            lowColor = color4;
            highColor = color4;
            interpolation = 0.5;
        } else if (unscaledHeight <= 0.76) {
            lowColor = color4;
            highColor = color5;
            interpolation = (unscaledHeight - 0.62) * 7.1426;
        } else if (unscaledHeight <= 0.81) {
            lowColor = color5;
            highColor = color5;
            interpolation = 0.5;
        } else if (unscaledHeight <= 0.95) {
            lowColor = color5;
            highColor = color6;
            interpolation = (unscaledHeight - 0.81) * 7.1426;
        } else {
            lowColor = color6;
            highColor = color6;
            interpolation = 0.5;
        }
        finalBaseColor = mix(toLinear(lowColor), toLinear(highColor), interpolation);
        riverColor = color1;
    } else {
        finalBaseColor = baseColor;
        riverColor = waterColor;
    }
    if (hasRivers) {
        float riverAlpha = texture(riverMapTexture, VertexIn.uv).r;
        riverAlpha = clamp((riverAlpha - 0.00002) * 25.0, 0, 1);
        finalBaseColor = mix(finalBaseColor, riverColor, riverAlpha);
        adjustedRoughness = adjustedRoughness * riverAlpha + roughness * (1 - riverAlpha);
        adjustedMetallic = adjustedMetallic * riverAlpha + metallic * (1 - riverAlpha);
        adjustedSpecIntensity = adjustedSpecIntensity * riverAlpha + specularIntensity * (1 - riverAlpha);
    } else {
        adjustedRoughness = roughness;
        adjustedMetallic = metallic;
        adjustedSpecIntensity = specularIntensity;
    }

    vec3 viewDirection = normalize(cameraPosition - vec3(VertexIn.position.xy, height));
    vec3 h = normalize(lightDirection + viewDirection);
    float nDotL = dot(normal, lightDirection);
    float nDotV = dot(normal, viewDirection);
    float nDotH = dot(normal, h);
    float vDotH = dot(viewDirection, h);
    vec3 reflect = 2 * nDotV * normal - viewDirection;

    float a = adjustedRoughness * adjustedRoughness;
    float a2 = a * a;
    float k = ((adjustedRoughness + 1) * (adjustedRoughness + 1)) / 8;

    vec3 directDiffuse = clamp(nDotL, 0.0, 1.0) * lightColor.rgb;
    directDiffuse = directDiffuse * (1 - clamp(pow(dot(lightDirection, vec3(0, 0, 1)), 7), 0.4, 1.0));
    vec3 indirectDiffuse = texture(irradianceMap, normal.xzy).rgb * indirectIntensity;
    vec3 totalDiffuse = ((directDiffuse + indirectDiffuse) * finalBaseColor.rgb * (1 - adjustedMetallic)) / pi;

    float temp = (nDotH * nDotH * (a2 - 1) + 1);
    float specD = a2 / (pi * temp * temp);
    float specG = (nDotV / (nDotV * (1 - k) + k)) * (nDotL / (nDotL * (1 - k) + k));
    vec3 f0Vec = mix(vec3(f0 * adjustedSpecIntensity), finalBaseColor.rgb, adjustedMetallic);
    vec3 specF = f0Vec + ((vec3(1.0) - f0Vec) * pow(2, ((-5.55473 * vDotH) - 6.98316) * vDotH));
    vec3 specular = max((vec3(specD) * vec3(specG) * specF) / (4 * nDotL * nDotV), 0.0);

    vec3 specReflect = textureLod(specularMap, reflect.xzy, adjustedRoughness * 6).rgb * indirectIntensity;
    vec2 envBrdf = texture(brdfMap, vec2(adjustedRoughness, clamp(nDotV, 0.0, 1.0))).rg;
    vec3 specIbl = specReflect * (specF * envBrdf.x + envBrdf.y);

    vec3 totalSpecular = specular * lightColor.rgb + specIbl * clamp(adjustedMetallic, 0.08 * adjustedSpecIntensity, 1.0);

    colorOut = vec4(toGamma(applyFog(worldPosition, (totalDiffuse + totalSpecular) * occlusion * occlusion * occlusion)), 1.0);
}
