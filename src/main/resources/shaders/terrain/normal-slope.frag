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

const vec3 up = vec3(0.0, 0.0, 1.0);

vec3 normal(vec3 a, vec3 b, vec3 c) {
    vec3 u = b - a;
    vec3 v = c - a;
    return vec3(u.y * v.z - u.z * v.y, u.z * v.x - u.x * v.z, u.x * v.y - u.y * v.x);
}

void main() {
    if (!gl_FrontFacing) {
        colorOut = vec4(0.076, 0.082, 0.212, 1.0);
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
    vec3 p1 = vec3(positionLeft, positionTop, northWestPixel);
    vec3 p2 = vec3(positionCenter, positionTop, northPixel);
    vec3 p3 = vec3(positionRight, positionTop, northEastPixel);
    vec3 p8 = vec3(positionLeft, positionMiddle, westPixel);
    vec3 p0 = vec3(positionCenter, positionMiddle, height);
    vec3 p4 = vec3(positionRight, positionMiddle, eastPixel);
    vec3 p7 = vec3(positionLeft, positionBottom, southWestPixel);
    vec3 p6 = vec3(positionCenter, positionBottom, southPixel);
    vec3 p5 = vec3(positionRight, positionBottom, southEastPixel);

    vec3 sum = normal(p0, p1, p8);
    sum += normal(p0, p8, p7);
    sum += normal(p0, p7, p6);
    sum += normal(p0, p6, p5);
    sum += normal(p0, p5, p4);
    sum += normal(p0, p4, p3);
    sum += normal(p0, p3, p2);
    sum += normal(p0, p2, p1);

    vec3 norm = normalize(sum);

    float angle = acos(dot(norm, up)) / pi;

    colorOut = vec4((norm.x + 1.0) * 0.5, (norm.y + 1.0) * 0.5, norm.z,  angle);
}
