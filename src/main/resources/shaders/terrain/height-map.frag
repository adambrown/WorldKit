#version 330

uniform mat4 modelViewProjectionMatrix;
uniform mat4 modelViewMatrix;
uniform mat3 normalMatrix;
uniform vec3 lightDirection;
uniform vec4 color1;
uniform vec4 color2;
uniform vec4 color3;
uniform vec4 color4;
uniform vec4 color5;
uniform vec4 color6;
uniform vec4 ambientColor;
uniform vec4 diffuseColor;
uniform vec4 specularColor;
uniform float shininess;
uniform float heightScale;
uniform float uvScale;
uniform int renderOptions;
uniform sampler2D heightMapTexture;
uniform sampler2D riverMapTexture;


in VertexData {
    vec2 position;
    vec2 uv;
} VertexIn;

layout(location = 0) out vec4 colorOut;

const ivec3 off = ivec3(-1, 0, 1);

const float gamma = 2.2;

vec3 toLinear(vec3 v) {
  return pow(v, vec3(gamma));
}

vec4 toLinear(vec4 v) {
  return vec4(toLinear(v.rgb), v.a);
}

vec3 toGamma(vec3 v) {
  return pow(v, vec3(1.0 / gamma));
}

vec4 toGamma(vec4 v) {
  return vec4(toGamma(v.rgb), v.a);
}

void main() {
    if (!gl_FrontFacing) {
        colorOut = vec4(0.076, 0.082, 0.212, 1.0);
        return;
    }
    vec2 size = vec2(uvScale * 2, 0.0);
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
    vec3 modelNormal = normalize(xNormal + crossNormal);
    vec3 normal = normalize(normalMatrix * modelNormal);
    float diffuse = max(dot(normal, lightDirection), 0.0) + 0.06;
    float adjustedShine = shininess;
    vec4 riverColor;
    vec4 lowColor;
    vec4 highColor;
    float interpolation;
    vec4 baseColor;
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
    if (!hasColor) {
        adjustedShine = 60.0;
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
        baseColor = mix(toLinear(lowColor), toLinear(highColor), interpolation);
        riverColor = color1;
    } else {
        if (unscaledHeight < 0.299) {
            baseColor = toLinear(color2);
        } else {
            baseColor = vec4(0.24, 0.052, 0.014, 1.0);
        }
        riverColor = color2;
    }
    if (hasRivers) {
        float riverAlpha = texture(riverMapTexture, VertexIn.uv).r;
        riverAlpha = min(1.0f, max(0.0f, riverAlpha - 0.00002) * 25.0);
        baseColor = mix(baseColor, toLinear(riverColor), riverAlpha);
        if (unscaledHeight >= 0.299) {
            adjustedShine = adjustedShine * riverAlpha + shininess * (1 - riverAlpha);
        }
    } else if (unscaledHeight >= 0.299) {
        adjustedShine = shininess;
    }
    vec4 lightColor = toLinear(vec4(1.0));
    vec4 diffuseColor = vec4(baseColor.rgb * lightColor.rgb * diffuse, 1.0);
    vec4 spec = vec4(0.0);
    vec3 eye = -normalize(vec3(modelViewMatrix * vec4(VertexIn.position.xy, height, 1.0)));
    float intensity = max(dot(normal, lightDirection), 0.0);
    if (intensity > 0.0) {
        vec3 halfVector = normalize(lightDirection + eye);
        spec = toLinear(specularColor) * pow(max(dot(halfVector, normal), 0.0), adjustedShine);
    }
    colorOut = toGamma(diffuseColor * max(intensity * toLinear(diffuseColor) + spec, toLinear(ambientColor)));
}
