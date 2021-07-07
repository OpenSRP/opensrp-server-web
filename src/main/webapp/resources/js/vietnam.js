//wrap auth-container in additional html
window.onload = function () {
    content = document.getElementById("content");
    clone = content.innerHTML;
    content.innerHTML = `
<div class="container-fluid">
<div class="row">
<div class="image col-lg col-xl"></div>
<div class="login-section col-lg col-xl">
    <div class="logo">
    <img src="images/vietnam/uni-vietnam.png" alt="" />
    <img src="images/vietnam/vietnam-moh.png" alt="" />
    <div class="moh-logo">
    </div>
    </div>
    <div class="form-section">
            <div class="innerdiv">
            <p class="sign-in">Sign in</p>
            <p class="miecd">MIECD VIETNAM</p>
            ${clone}
        </div>
    </div>
</div>
</div>
</div>`;
};