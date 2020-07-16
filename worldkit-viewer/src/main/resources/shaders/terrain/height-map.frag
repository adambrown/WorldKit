#version 330

uniform mat4 modelViewProjectionMatrix;
uniform mat4 modelViewMatrix;
uniform mat3 normalMatrix;
uniform vec3 lightDirection;
uniform vec3 cameraPosition;
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
uniform float occlusionPower;
uniform float heightScale;
uniform float heightScaleMeters;
uniform float colorHeightScale;
uniform float uvScale;
uniform float horizonBlend;
uniform float lightMaxFogEffect;
uniform float gradientOffset;
uniform mat3 fogParams;
uniform int renderOptions;
uniform sampler2D heightMapTexture;
uniform sampler2D normalAoTexture;
uniform sampler2D riverMapTexture;
uniform sampler2D brdfMap;
uniform samplerCube irradianceMap;
uniform samplerCube specularMap;
uniform sampler2D inscatterTexture;
uniform sampler2D inscatterHorizonTexture;
uniform sampler2D lossTexture;
uniform sampler2D demGradientTexture;


in VertexData {
    vec2 position;
    vec2 uv;
} VertexIn;

layout(location = 0) out vec4 colorOut;

const ivec3 off = ivec3(-1, 0, 1);

const float gamma = 2.2;

const float iGamma = 1 / gamma;

const float pi = 3.1415926538;

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

float dggx(float nDotH, float a2) {
    float temp  = (nDotH * nDotH * (a2 - 1.0) + 1.0);
    return a2 / (pi * temp * temp);
}

float gggx(float nDotV, float nDotL, float k)
{
    return (nDotV / (nDotV * (1.0 - k) + k)) * (nDotL / (nDotL * (1.0 - k) + k));
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
    float colorHeight = unscaledHeight * colorHeightScale;
    float viewHeight = unscaledHeight * heightScale;

    vec3 fragmentPosition = vec3(VertexIn.position.xy, viewHeight);

    vec4 normalAoColor = texture(normalAoTexture, VertexIn.uv);
    vec3 normal = normalize((normalAoColor.rgb - 0.5) * 2.0);
    float occlusion = normalAoColor.a;

    float adjustedMetallic = waterMetallic;
    float adjustedRoughness = waterRoughness;
    float adjustedSpecIntensity = waterSpecularIntensity;
    vec4 riverColor;
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
        finalBaseColor = texture(demGradientTexture, vec2(colorHeight - gradientOffset, 0.0));
        riverColor = waterColor;
    } else {
        finalBaseColor = baseColor;
        riverColor = waterColor;
    }
    if (hasRivers) {
        float riverAlpha = texture(riverMapTexture, VertexIn.uv).r;
        finalBaseColor = mix(finalBaseColor, riverColor, riverAlpha);
        adjustedRoughness = adjustedRoughness * riverAlpha + roughness * (1 - riverAlpha);
        adjustedMetallic = adjustedMetallic * riverAlpha + metallic * (1 - riverAlpha);
        adjustedSpecIntensity = adjustedSpecIntensity * riverAlpha + specularIntensity * (1 - riverAlpha);
    } else {
        adjustedRoughness = roughness;
        adjustedMetallic = metallic;
        adjustedSpecIntensity = specularIntensity;
    }

    vec3 viewDirection = normalize(cameraPosition - fragmentPosition);
    vec3 halfVector = normalize(lightDirection + viewDirection);
    vec3 reflectVector = reflect(-viewDirection, normal);
    float nDotL = max(dot(normal, lightDirection), 0.0);
    float nDotV = max(dot(normal, viewDirection), 0.0);
    float nDotH = max(dot(normal, halfVector), 0.0);
    float vDotH = max(dot(viewDirection, halfVector), 0.0);

    float a = adjustedRoughness * adjustedRoughness;
    float a2 = a * a;

    float d = dggx(nDotH, a2);

    float k = ((adjustedRoughness + 1.0) * (adjustedRoughness + 1.0)) / 8.0;
    float g = gggx(nDotV, nDotL, k);

    vec3 f0 = mix(vec3(0.08 * specularIntensity), finalBaseColor.rgb, adjustedMetallic);
    vec3 f = f0 + (max(vec3(1.0 - adjustedRoughness), f0) - f0) * pow(1.0 - vDotH, 5.0);

    vec3 specular = (d * g * f) / max(4.0 * nDotV * nDotL, 0.001);

    vec3 kD = (vec3(1.0) - f) * (1 - adjustedMetallic);

    vec3 directLight = (kD * finalBaseColor.rgb / pi + specular) * lightColor.rgb * nDotL;

    vec3 irradiance = texture(irradianceMap, normal.xzy).rgb * indirectIntensity;
    vec3 diffuse = irradiance * finalBaseColor.rgb;

    vec3 specReflect = textureLod(specularMap, reflectVector.xzy, adjustedRoughness * 6).rgb * indirectIntensity;
    vec2 envBrdf = texture(brdfMap, vec2(nDotV, adjustedRoughness)).rg;
    vec3 specIbl = specReflect * (f * envBrdf.x + envBrdf.y);

    vec3 ambient = (kD * diffuse + specIbl) * pow(occlusion, occlusionPower);

    colorOut = vec4(toGamma(applyFog(fragmentPosition, directLight * occlusion + ambient)), 1.0);
}
