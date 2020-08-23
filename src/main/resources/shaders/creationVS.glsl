#version 140
precision highp float;
in vec3 pos;
in vec3 color;

uniform mat4 projMatrix;
uniform mat4 viewMatrix;
uniform mat4 transformationMatrix;
uniform float modelHeight;
uniform float builtProgress;

out vec3 worldPos;
out vec3 vertexColor;
out float cutHeight;
void main(){
    vertexColor=color;
    cutHeight = modelHeight*builtProgress-pos.y;
    vec4 aPos = transformationMatrix*vec4(pos, 1);
    gl_Position =projMatrix*viewMatrix*aPos;
}