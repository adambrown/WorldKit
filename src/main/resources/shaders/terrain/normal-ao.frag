#version 330

uniform float heightScale;
uniform float uvScale;
uniform sampler2D heightMapTexture;


in VertexData {
    vec2 uv;
} VertexIn;

layout(location = 0) out vec4 colorOut;

const float pi = 3.14159265359;

const ivec3 off = ivec3(-1, 0, 1);

//const vec3 up = vec3(0.0, 0.0, 1.0);

vec3 normal(vec3 a, vec3 b, vec3 c) {
    vec3 u = b - a;
    vec3 v = c - a;
    return (u.yzx * v.zxy) - (u.zxy * v.yzx);
}

void main() {
    if (!gl_FrontFacing) {
        colorOut = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }
    float positionLeft = 0.0;
    float positionCenter = uvScale;
    float positionRight = uvScale * 2.0;
    float positionBottom = positionLeft;
    float positionMiddle = positionCenter;
    float positionTop = positionRight;

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
    vec3 p0 = vec3(positionCenter, positionMiddle, height);
    vec3 p1 = vec3(positionLeft, positionTop, northWestPixel);
    vec3 p2 = vec3(positionCenter, positionTop, northPixel);
    vec3 p3 = vec3(positionRight, positionTop, northEastPixel);
    vec3 p4 = vec3(positionRight, positionMiddle, eastPixel);
    vec3 p5 = vec3(positionRight, positionBottom, southEastPixel);
    vec3 p6 = vec3(positionCenter, positionBottom, southPixel);
    vec3 p7 = vec3(positionLeft, positionBottom, southWestPixel);
    vec3 p8 = vec3(positionLeft, positionMiddle, westPixel);

    vec3 sum = normal(p0, p1, p8);
    sum += normal(p0, p8, p7);
    sum += normal(p0, p7, p6);
    sum += normal(p0, p6, p5);
    sum += normal(p0, p5, p4);
    sum += normal(p0, p4, p3);
    sum += normal(p0, p3, p2);
    sum += normal(p0, p2, p1);

    vec3 norm = normalize(sum);
    vec3 renderNorm = norm * 0.5 + 0.5;

    float aosum = 0;
    float count = 0;
    float texSize = textureSize(heightMapTexture, 0).x;
    vec3 thisPoint = vec3(VertexIn.uv * texSize * uvScale, height);
    for(int i=64; i<1089; i++){
        float s = i/1088.0;
        float a = sqrt(s*512);
        float b = sqrt(s);
        vec2 otherUV = VertexIn.uv + (vec2(sin(a) * b, cos(a) * b) * 0.2f);
        vec3 otherPoint = otherUV.x <= 1 && otherUV.x >= 0 && otherUV.y <= 1 && otherUV.y >= 0 ? vec3(otherUV * texSize * uvScale, texture(heightMapTexture, otherUV).r * heightScale) : vec3(otherUV * texSize * uvScale, 0.0f);
        vec3 heading = otherPoint - thisPoint;
        float dot = dot(norm, normalize(heading));
        float occlusion = dot / sqrt(length(heading));
        float contribution = dot > 0 ?  0.4 + 0.6 * (1 - b) : 0.0;
        count += contribution;
        aosum += occlusion * contribution;
    }

    float aoScale = heightScale * 0.015;

    float ao = 1 - clamp((aosum / count) * aoScale, 0, 1);

//    float angle = acos(dot(norm, up)) / pi;

    colorOut = vec4(renderNorm,  ao);
}
