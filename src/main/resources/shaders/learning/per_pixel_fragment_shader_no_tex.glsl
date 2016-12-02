#version 410

uniform vec3 lightPosition;
uniform vec4 color;

in VertexData {
    vec3 position;
    vec3 normal;
} VertexIn;

layout(location = 0) out vec4 colorOut;

void main() {
    float distance = length(lightPosition - VertexIn.position);
    vec3 lightVector = normalize(lightPosition - VertexIn.position);
    float diffuse;

    if (gl_FrontFacing) {
        diffuse = max(dot(VertexIn.normal, lightVector), 0.0);
    } else {
        diffuse = max(dot(-VertexIn.normal, lightVector), 0.0);
    }
    diffuse = diffuse * (1.0 / (1.0 + (0.02 * distance)));
    diffuse = diffuse + 0.15;
    colorOut = (color * diffuse);
}                                                                     	

