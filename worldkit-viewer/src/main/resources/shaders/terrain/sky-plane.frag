#version 330

uniform vec3 lightDirection;
uniform vec4 lightColor;
uniform vec3 cameraPosition;
uniform float horizonBlend;
uniform float lightMaxFogEffect;
uniform mat3 fogParams;
uniform samplerCube skyMap;
uniform sampler2D inscatterTexture;
uniform sampler2D inscatterHorizonTexture;
uniform sampler2D lossTexture;

in VertexData {
    vec3 cameraDir;
} VertexIn;

layout(location = 0) out vec4 colorOut;

const float gamma = 2.2;

const float iGamma = 1 / gamma;

const float skyDistance = 100;

const float lightPower = 4;

const float pi = 3.1415926538;

const float distanceScale = 0.006153846;

vec3 toLinear(vec3 v) {
    return pow(v, vec3(gamma));
}

vec3 toGamma(vec3 v) {
    return pow(v, vec3(iGamma));
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

void main() {
    vec3 viewDir = normalize(VertexIn.cameraDir);

    colorOut = vec4(toGamma(skyLookup(viewDir, cameraPosition)), 1);
}

