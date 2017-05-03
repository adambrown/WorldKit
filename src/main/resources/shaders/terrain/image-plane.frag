#version 330

uniform sampler2D graphTexture;
uniform sampler2D maskTexture;

in VertexData {
    vec2 uv;
} VertexIn;

layout(location = 0) out vec4 colorOut;

const ivec2 xx = ivec2(-1, -1);
const ivec2 yx = ivec2(0, -1);
const ivec2 zx = ivec2(1, -1);
const ivec2 xy = ivec2(-1, 0);
const ivec2 zy = ivec2(1, 0);
const ivec2 xz = ivec2(-1, 1);
const ivec2 yz = ivec2(0, 1);
const ivec2 zz = ivec2(1, 1);

const ivec3 off = ivec3(-1, 0, 1);

float distance2(vec2 p1, vec2 p2) {
    vec2 delta = p2 - p1;
    return dot(delta, delta);
}

void main() {
    vec2 northWestPixel = textureOffset(graphTexture, VertexIn.uv, xx).rg;
    vec2 northPixel =     textureOffset(graphTexture, VertexIn.uv, yx).rg;
    vec2 northEastPixel = textureOffset(graphTexture, VertexIn.uv, zx).rg;
    vec2 westPixel =      textureOffset(graphTexture, VertexIn.uv, xy).rg;
    vec2 centerPixel =    texture(graphTexture, VertexIn.uv).rg;
    vec2 eastPixel =      textureOffset(graphTexture, VertexIn.uv, zy).rg;
    vec2 southWestPixel = textureOffset(graphTexture, VertexIn.uv, xz).rg;
    vec2 southPixel =     textureOffset(graphTexture, VertexIn.uv, yz).rg;
    vec2 southEastPixel = textureOffset(graphTexture, VertexIn.uv, zz).rg;

    float northWestDist2 = distance2(VertexIn.uv, northWestPixel);
    float northDist2 =     distance2(VertexIn.uv, northPixel);
    float northEastDist2 = distance2(VertexIn.uv, northEastPixel);
    float westDist2 =      distance2(VertexIn.uv, westPixel);
    float centerDist2 =    distance2(VertexIn.uv, centerPixel);
    float eastDist2 =      distance2(VertexIn.uv, eastPixel);
    float southWestDist2 = distance2(VertexIn.uv, southWestPixel);
    float southDist2 =     distance2(VertexIn.uv, southPixel);
    float southEastDist2 = distance2(VertexIn.uv, southEastPixel);

    ivec2 quadrant = off.yy;
    float minDist = centerDist2;
    if (northWestDist2 < minDist) {
        minDist = northWestDist2;
        quadrant = off.xx;
    }
    if (northDist2 < minDist) {
        minDist = northDist2;
        quadrant = off.yx;
    }
    if (northEastDist2 < minDist) {
        minDist = northEastDist2;
        quadrant = off.zx;
    }
    if (westDist2 < minDist) {
        minDist = westDist2;
        quadrant = off.xy;
    }
    if (eastDist2 < minDist) {
        minDist = eastDist2;
        quadrant = off.zy;
    }
    if (southWestDist2 < minDist) {
        minDist = southWestDist2;
        quadrant = off.xz;
    }
    if (southDist2 < minDist) {
        minDist = southDist2;
        quadrant = off.yz;
    }
    if (southEastDist2 < minDist) {
        minDist = southEastDist2;
        quadrant = off.zz;
    }

    int id;
    if (quadrant == off.yy) {
        id = int(round(texture(maskTexture, VertexIn.uv).r * 16.0f));
    } else if (quadrant == xx) {
        id = int(round(textureOffset(maskTexture, VertexIn.uv, xx).r * 16.0f));
    } else if (quadrant == yx) {
        id = int(round(textureOffset(maskTexture, VertexIn.uv, yx).r * 16.0f));
    } else if (quadrant == zx) {
        id = int(round(textureOffset(maskTexture, VertexIn.uv, zx).r * 16.0f));
    } else if (quadrant == xy) {
        id = int(round(textureOffset(maskTexture, VertexIn.uv, xy).r * 16.0f));
    } else if (quadrant == zy) {
        id = int(round(textureOffset(maskTexture, VertexIn.uv, zy).r * 16.0f));
    } else if (quadrant == xz) {
        id = int(round(textureOffset(maskTexture, VertexIn.uv, xz).r * 16.0f));
    } else if (quadrant == yz) {
        id = int(round(textureOffset(maskTexture, VertexIn.uv, yz).r * 16.0f));
    } else if (quadrant == zz) {
        id = int(round(textureOffset(maskTexture, VertexIn.uv, zz).r * 16.0f));
    }

    switch (id) {
        case 1: colorOut = vec4(0.28627452f, 0.93333334f, 0.28627452f, 1.0f); break;
        case 2: colorOut = vec4(1.0f, 0.6392157f, 0.9411765f, 1.0f); break;
        case 3: colorOut = vec4(1.0f, 1.0f, 0.5294118f, 1.0f); break;
        case 4: colorOut = vec4(0.627451f, 0.24313726f, 0.8901961f, 1.0f); break;
        case 5: colorOut = vec4(0.8392157f, 0.53333336f, 0.21960784f, 1.0f); break;
        case 6: colorOut = vec4(0.41568628f, 0.41568628f, 1.0f, 1.0f); break;
        case 7: colorOut = vec4(0.9647059f, 0.34509805f, 0.34509805f, 1.0f); break;
        case 8: colorOut = vec4(0.1882353f, 0.8039216f, 0.80784315f, 1.0f); break;
        case 9: colorOut = vec4(0.17254902f, 0.57254905f, 0.17254902f, 1.0f); break;
        case 10: colorOut = vec4(0.7607843f, 0.16862746f, 0.65882355f, 1.0f); break;
        case 11: colorOut = vec4(0.69803923f, 0.7019608f, 0.1764706f, 1.0f); break;
        case 12: colorOut = vec4(0.38431373f, 0.18039216f, 0.5372549f, 1.0f); break;
        case 13: colorOut = vec4(0.49411765f, 0.34117648f, 0.19215687f, 1.0f); break;
        case 14: colorOut = vec4(0.16470589f, 0.16470589f, 0.6627451f, 1.0f); break;
        case 15: colorOut = vec4(0.61960787f, 0.16078432f, 0.16078432f, 1.0f); break;
        case 16: colorOut = vec4(0.20392157f, 0.45490196f, 0.45490196f, 1.0f); break;
        default: colorOut = vec4(0.0, 0.0, 0.0, 1.0); break;
    }
}
