#version 330

uniform vec3 lightDirection;
uniform vec3 cameraPosition;
uniform vec4 lightColor;
uniform float horizonBlend;
uniform float lightMaxFogEffect;
uniform vec4 baseColor;
uniform vec4 shallowColor;
uniform float roughness;
uniform float metallic;
uniform float specularIntensity;
uniform float indirectIntensity;
uniform mat3 normalOffsets;
uniform vec3 fadeStarts;
uniform vec3 fadeEnds;
uniform vec4 normalStrengths;
uniform float heightScale;
uniform float waterLevel;
uniform float depthPower;
uniform mat3 fogParams;
uniform sampler2D brdfMap;
uniform samplerCube irradianceMap;
uniform samplerCube skyMap;
uniform sampler2D waterNormalTexture;
uniform sampler2D heightMapTexture;
uniform sampler2D inscatterTexture;
uniform sampler2D inscatterHorizonTexture;
uniform sampler2D lossTexture;


in VertexData {
    vec3 position;
} VertexIn;

layout(location = 0) out vec4 colorOut;

const float gamma = 2.2;

const float iGamma = 1 / gamma;

const float pi = 3.1415926538;

const float f0 = 0.08;

const float uvScale = 1 / 2560.0;

const float skyDistance = 100;

const float lightPower = 4;

const float distanceScale = 0.006153846;

vec3 toLinear(vec3 v) {
    return pow(v, vec3(gamma));
}

vec3 toGamma(vec3 v) {
    return pow(v, vec3(iGamma));
}

float dggx(float nDotH, float a2) {
    float temp  = (nDotH * nDotH * (a2 - 1.0) + 1.0);
    return a2 / (pi * temp * temp);
}

float gggx(float nDotV, float nDotL, float k)
{
    return (nDotV / (nDotV * (1.0 - k) + k)) * (nDotL / (nDotL * (1.0 - k) + k));
}

vec3 skyLookup(vec3 reflect, vec3 worldPosition) {
    vec3 viewDir = reflect;
    vec3 pixelColor = toLinear(textureLod(skyMap, viewDir.xzy, 0).rgb);

    vec3 fogColor = fogParams[0].xyz;
    float atmosphericFogDensity = fogParams[1].x;
    float exponentialFogDensity = fogParams[1].y;
    float exponentialFogHeightFalloff = fogParams[1].z;
    float fogHeightClampPower = fogParams[2].x;
    float fogOn = fogParams[2].y;
    float heightFalloff = exponentialFogHeightFalloff / exponentialFogDensity;

    vec3 distanceVector = viewDir * skyDistance;
    float distance = skyDistance;

    float lDotV = clamp(dot(lightDirection, viewDir), 0, 1);
    float lightFacing = 1 - clamp(acos(lDotV) / pi, 0, 1);
    float lightColorLength = dot(lightColor.rgb, lightColor.rgb);
    vec3 sunDisk = pow(smoothstep(0.988, 1.0, lightFacing), 0.85) * (lightColorLength > 0 ? normalize(lightColor.rgb) : vec3(0)) * 1.3;

    float heightDifference = distanceVector.z * distanceScale;

    float scatterU = clamp(1 - exp(-distance * atmosphericFogDensity) + (heightFalloff * exp(-worldPosition.z * distanceScale * exponentialFogDensity) * ((1 - exp(-distance * heightDifference * exponentialFogDensity)) / heightDifference)) * exp((worldPosition.z * distanceScale + heightDifference) * fogHeightClampPower), 0, 1);
    float scatterV = 1 - clamp((1 + dot(viewDir, vec3(0, 0, 1))) * 0.5, 0, 1);
    vec2 scatterLookup = vec2(scatterU, scatterV);

    vec3 loss = mix(vec3(1.0), texture(lossTexture, scatterLookup).rgb, fogOn);

    vec3 fogWithLightColor = mix(vec3(0.0), mix(fogColor, lightColor.rgb * 1.3, vec3((pow(lDotV, lightPower) * lightMaxFogEffect))), fogOn);

    vec3 inscatter = mix(texture(inscatterTexture, scatterLookup).rgb, texture(inscatterHorizonTexture, scatterLookup).rgb, horizonBlend);

    return clamp(pixelColor * loss + fogWithLightColor * inscatter + sunDisk, 0, 1);
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

    vec2 uv = (VertexIn.position.xy * uvScale) + 0.5;
    uv.y = 1 - uv.y;
    float unscaledHeight = (uv.x >= 0 && uv.x <= 1 && uv.y >= 0 && uv.y <= 1) ? texture(heightMapTexture, uv).r : 0.0;
    float depth = clamp(waterLevel - unscaledHeight, 0.0, 1.0);
    float depth1 = smoothstep(-0.002, 0.07, depth);
    float depthAlpha = 1 - pow(depth1, depthPower);

    vec3 viewVector = cameraPosition - VertexIn.position;
    float viewDist = length(viewVector);
    vec3 viewDirection = normalize(viewVector);

    float alpha1 = 1 - smoothstep(fadeStarts.x, fadeEnds.x, viewDist);
    float alpha2 = 1 - smoothstep(fadeStarts.y, fadeEnds.y, viewDist);
    float alpha3 = 1 - smoothstep(fadeStarts.z, fadeEnds.z, viewDist);

    vec3 flatNormal = vec3(0.0, 0.0, 1.0);
    vec3 normalHigh1 = normalize(mix(flatNormal, texture(waterNormalTexture, VertexIn.position.xy * normalOffsets[0][0]).xyz * 2 - 1, alpha1));
    vec3 normalHigh2 = normalize(mix(flatNormal, texture(waterNormalTexture, VertexIn.position.xy * normalOffsets[0][1]).xyz * 2 - 1, alpha2));
    vec3 normalHigh3 = normalize(mix(flatNormal, texture(waterNormalTexture, VertexIn.position.xy * normalOffsets[0][2]).xyz * 2 - 1, alpha3));
    vec3 normalHigh4 = texture(waterNormalTexture, VertexIn.position.xy * normalOffsets[1][0]).xyz * 2 - 1;
    vec3 normalHigh5 = texture(waterNormalTexture, VertexIn.position.xy * normalOffsets[1][1]).xyz * 2 - 1;
    vec3 normal = normalize(vec3(normalHigh1.xy * normalStrengths.x + normalHigh2.xy * normalStrengths.y + normalHigh3.xy * normalStrengths.z + normalHigh4.xy + normalHigh5.xy, normalStrengths.w));

    vec3 adjustedColor = mix(baseColor.rgb, shallowColor.rgb, depthAlpha);

    vec3 halfVector = normalize(lightDirection + viewDirection);
    vec3 reflectVector = reflect(-viewDirection, normal);
    float nDotL = max(dot(normal, lightDirection), 0.0);
    float nDotV = max(dot(normal, viewDirection), 0.0);
    float nDotH = max(dot(normal, halfVector), 0.0);
    float vDotH = max(dot(viewDirection, halfVector), 0.0);

    float a = roughness * roughness;
    float a2 = a * a;

    float d = dggx(nDotH, a2);

    float k = ((roughness + 1.0) * (roughness + 1.0)) / 8.0;
    float g = gggx(nDotV, nDotL, k);

    vec3 f0 = mix(vec3(0.08 * specularIntensity), adjustedColor.rgb, metallic);
    vec3 f = f0 + (max(vec3(1.0 - roughness), f0) - f0) * pow(1.0 - vDotH, 5.0);

    vec3 specular = (d * g * f) / max(4.0 * nDotV * nDotL, 0.001);

    vec3 kD = (vec3(1.0) - f) * (1 - metallic);

    vec3 directLight = (kD * adjustedColor.rgb / pi + specular) * lightColor.rgb * nDotL;

    vec3 irradiance = texture(irradianceMap, normal.xyz).rgb * indirectIntensity;
    vec3 diffuse = irradiance * adjustedColor.rgb;

    vec3 specReflect = skyLookup(reflectVector, VertexIn.position).rgb * indirectIntensity;
    vec2 envBrdf = texture(brdfMap, vec2(nDotV, roughness)).rg;
    vec3 specIbl = specReflect * (f * envBrdf.x + envBrdf.y);

    vec3 ambient = kD * diffuse + specIbl;

    colorOut = vec4(toGamma(applyFog(VertexIn.position, directLight + ambient)), 1.0);
}
